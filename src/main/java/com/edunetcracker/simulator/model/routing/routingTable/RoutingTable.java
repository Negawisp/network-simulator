package com.edunetcracker.simulator.model.routing.routingTable;

import com.edunetcracker.simulator.model.DBObject;
import com.edunetcracker.simulator.model.element.Router;
import com.edunetcracker.simulator.model.routing.RouteSource;
import com.edunetcracker.simulator.model.routing.routingTableEntry.RoutingTableEntry;
import com.edunetcracker.simulator.service.routingService.IpService;
import com.edunetcracker.simulator.service.status.SequenceStatus;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;


@Entity(name="routing_table")
public class RoutingTable implements DBObject<RoutingTable> {
    @Transient
    private static Logger logger = LoggerFactory.getLogger(RoutingTable.class);


    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Setter
    @Getter
    private long id;

    @OneToOne(mappedBy="routingTable")
    @Setter
    @Getter
    private Router router;

    @OneToMany(cascade=CascadeType.ALL,
               fetch=FetchType.LAZY)
    @JoinColumn(name="routing_table_id")
    private Set<RoutingTableEntry> routes;

    @Setter
    @Getter
    @Transient
    private Set<RoutingTableEntry> runningRoutes;


    public RoutingTable() {
        routes = new HashSet<>();
        runningRoutes = new HashSet<>();
    }


    @Override
    public SequenceStatus copyRefs(RoutingTable another) {
        router = another.router;
        routes = another.routes;
        runningRoutes = another.runningRoutes;
        return SequenceStatus.OK;
    }

    @Override
    public SequenceStatus copyAutogeneratedValues(RoutingTable another) {
        id = another.id;
        return SequenceStatus.OK;
    }

    public SequenceStatus ownerOK () {
        if (null == router) {
            return SequenceStatus.ROUTING_TABLE_NO_ROUTER;
        }
        if (router.getRoutingTable() != this) {
            return SequenceStatus.OWNER_INCONSISTENCY;
        }
        return SequenceStatus.OK;
    }

    public void addRoute (RoutingTableEntry route) {
        runningRoutes.add(route);
        if (RouteSource.STATIC.equals(route.getRouteSource())) {
            routes.add(route);
        }
    }

    public void loadSavedRoutesToRunning() {
        runningRoutes.addAll(routes);
    }

    public RoutingTableEntry getEntryByIp (Integer ip) {
        int mask = -1;
        RoutingTableEntry retEntry = null;
        for (RoutingTableEntry entry : runningRoutes) {
            if (IpService.isInSubnet(ip, entry.getIp(), entry.getMask())) {
                return entry;
                //TODO: Make IP and Masks LONG again, not INT.
                //      Let's work with positive numbers.
                /*
                int curMask = entry.getMask();
                if (curMask > mask) {
                    mask = curMask;
                    retEntry = entry;
                }
                */
            }
        }
        return retEntry;
    }
}
