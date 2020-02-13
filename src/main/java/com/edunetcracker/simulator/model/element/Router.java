package com.edunetcracker.simulator.model.element;

import com.edunetcracker.simulator.model.context.NEContext;
import com.edunetcracker.simulator.model.dataUnit.ip.IP;
import com.edunetcracker.simulator.model.dataUnit.DataUnit;
import com.edunetcracker.simulator.model.port.Port;
import com.edunetcracker.simulator.model.port.RouterPort;
import com.edunetcracker.simulator.model.routing.RouteSource;
import com.edunetcracker.simulator.model.routing.routingTable.RoutingTable;
import com.edunetcracker.simulator.model.routing.routingTableEntry.RoutingTableEntry;
import com.edunetcracker.simulator.service.context.ContextService;
import com.edunetcracker.simulator.service.routingService.IpService;
import com.edunetcracker.simulator.service.routingService.RoutingTableService;
import com.edunetcracker.simulator.service.status.SequenceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.*;
import javax.persistence.*;


@Setter
@Getter
@Entity
public class Router extends NetworkElement {
    private static Logger logger = LoggerFactory.getLogger(Router.class);

    @JsonProperty
    @OneToMany(mappedBy = "router",
               cascade = CascadeType.ALL,
               fetch = FetchType.LAZY)
    private List<RouterPort> ports;


    @JsonProperty
    @OneToOne(cascade=CascadeType.ALL,
              fetch = FetchType.EAGER)
    @JoinColumn(name="routing_table_id",
                referencedColumnName="id")
    private RoutingTable routingTable;


    public Router() {
        super();
        routingTable = new RoutingTable();
        routingTable.setRouter(this);
        ports = new ArrayList<>();
    }

    @Override
    public SequenceStatus copyRefs(NetworkElement another) {
        if (!(another instanceof Router)) {
            SequenceStatus.PARAMETER_TYPE_INCONSISTENCY.logError("Router");
            return SequenceStatus.PARAMETER_TYPE_INCONSISTENCY;
        }
        super.copyRefs(another);
        Router anotherRouter = (Router)another;
        this.ports = anotherRouter.ports;
        this.routingTable = anotherRouter.routingTable;
        return SequenceStatus.OK;
    }

    @Override
    public SequenceStatus copyAutogeneratedValues(NetworkElement another) {
        if (!(another instanceof Router)) {
            SequenceStatus.PARAMETER_TYPE_INCONSISTENCY.logError("Router");
            return SequenceStatus.PARAMETER_TYPE_INCONSISTENCY;
        }
        super.copyAutogeneratedValues(another);
        return SequenceStatus.OK;
    }

    @Override
    protected void processContexts() {
        Iterator contextsIterator = this.contexts.iterator();
        while (contextsIterator.hasNext()) {
            NEContext context = (NEContext) contextsIterator.next();
            List<DataUnit> dataUnits = context.performAction();
            processDataUnits(dataUnits);
            if (!context.isAlive()) {
                contextsIterator.remove();
            }
        }
    }

    @Override
    protected void processInputTraffic() {
        Object[] nonemptyPortsArr = ports.stream()
                                   .filter(Port::hasInput)
                                   .toArray();
        LinkedList nonemptyPorts = new LinkedList(Arrays.asList(nonemptyPortsArr));
        ListIterator<RouterPort> portIter = nonemptyPorts.listIterator();

        int n = 0;
        while (n < inputProcessingRate) {
            if (!portIter.hasNext()) {
                if (!nonemptyPorts.isEmpty()) {
                    portIter = nonemptyPorts.listIterator();
                } else { break; }
            }
            RouterPort curPort = portIter.next();

            if (processPortInput(curPort)) { ++n; }
            else {
                portIter.remove();
            }
        }
    }

    private boolean processPortInput (RouterPort port) {
        DataUnit dataUnit = port.getIn().poll();
        if (null == dataUnit) {
            return false;
        }
        processDataUnit(dataUnit);

        return true;
    }

    private void processDataUnits (List<DataUnit> dataUnits) {
        for (DataUnit dataUnit : dataUnits) {
            if (null != dataUnit) {
                processDataUnit(dataUnit);
            }
        }
    }

    private void processDataUnit (DataUnit dataUnit) {
        logger.info("Processing dataUnit");
        if (dataUnit.getType() == DataUnit.Type.IP) {
            processIp((IP)dataUnit);
        }
    }

    private void processIp (IP packet) {
        logger.info("Processing IP.");
        Optional<RouterPort> port = findLeadingPort(packet.getDestinationIp());
        if (null == port) {
            logger.info("Destination {} is unreachable.", packet.getDestinationIp());
            //This code is to create a "Destination unreachable" packet and reroute it
            //port = findPortByIp(packet.getSourceIp());
            //packet = IpBuilder.destinationUnreachable(packet, port.get().getIp());
            return;
        }
        if (port.isPresent()) {
            logger.info("Destination \"{}\" is reachable through port w/ ip \"{}\"",
                    packet.getDestinationIp(), port.get().getIp());
            if (null == packet.getSourceIp()) {
                packet.setSourceIp(port.get().getIp());
            }
            port.get().push(packet);
            NEContext context = ContextService.fromTransitionalIp(packet);
            if (null != context) {
                contexts.add(context);
            }
        } else {
            logger.info("The router is a destination \"{}\"", packet.getDestinationIp());
            NEContext context = ContextService.fromAcceptedIp(packet);
            if (null != context) {
                contexts.add(context);
            }
        }
    }

    @Override
    protected void processCommands() {

    }

    public void initializeRunningRoutes() {
        if (null == routingTable || null == ports) {
            SequenceStatus.FAILED_INITIALIZATION.logError("Router", "routingTable or ports");
            throw new NullPointerException();
        }
        routingTable.loadSavedRoutesToRunning();
        for (RouterPort port : ports) {
            RoutingTableEntry[] dcroutes = RoutingTableService.constructDirectConnection(port);
            if (null != dcroutes) {
                routingTable.addRoute(dcroutes[0]);
                routingTable.addRoute(dcroutes[1]);
            }
        }
    }


    /**
     * Searches for a port leading to the given destination IP.
     * @param ip
     * @return 1) Optional with null,       if port w/ destination IP is owned by the router
     *         2) Optional with instance,   if the appropriate port was found
     *         3) Null,                     if the router can't route to the given IP.
     */
    public Optional<RouterPort> findLeadingPort (Integer ip) {
        RoutingTableEntry entry = routingTable.getEntryByIp(ip);
        if (null == entry) {
            return null;
        }
        if (RouteSource.LOCAL_PORT.equals(entry.getRouteSource())) {
            return Optional.empty();
        }
        for (RouterPort port : ports) {
            if (IpService.isInSubnet(entry.getIp(), port.getIp(), port.getMask())) {
                return Optional.of(port);
            }
        }
        return null;
    }

    /**
     * Returns port with number given. (Each port has it's unique number in a NetworkElement.)
     * @param portNumber
     * @return
     */
    public RouterPort getPort(int portNumber) {
        return ports.get(portNumber-1);
    }
}