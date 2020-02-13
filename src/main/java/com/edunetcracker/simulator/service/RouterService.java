package com.edunetcracker.simulator.service;


import com.edunetcracker.simulator.database.repository.networkElementRepository.RouterRepository;
import com.edunetcracker.simulator.model.element.Router;
import com.edunetcracker.simulator.model.port.RouterPort;
import com.edunetcracker.simulator.service.configurers.RouterConfigurer;

import com.edunetcracker.simulator.service.routingService.RoutingTableService;
import com.edunetcracker.simulator.service.status.SequenceStatus;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouterService extends DBService<Router> {
    private static Logger logger = LoggerFactory.getLogger(RouterService.class);

    private final RouterRepository routerRepository;
    private final RouterConfigurer routerConfigurer;
    private final PortService portService;
    private final RoutingTableService routingTableService;

    @Getter
    private List<Router> loadedRouters = new ArrayList<>();

    public RouterService(RouterRepository routerRepository, RouterConfigurer routerConfigurer, PortService portService, RoutingTableService routingTableService) {
        this.routerRepository = routerRepository;
        this.routerConfigurer = routerConfigurer;
        this.portService = portService;
        this.routingTableService = routingTableService;
    }

    @Override
    public Router getLoaded(long id) {
        for (Router router : loadedRouters) {
            if (router.getIdNE() == id) {
                return router;
            }
        }
        return null;
    }

    @Override
    public Router get (long id) {
        Router loadedRouter = getLoaded(id);
        if (null != loadedRouter) {
            return loadedRouter;
        }
        Optional<Router> newlyLoaded = routerRepository.findByIdNE(id);
        if (!newlyLoaded.isPresent()) {
            SequenceStatus.NOT_FOUND_IN_DATABASE.logWarning("Router", id);
            return null;
        }
        loadedRouters.add(newlyLoaded.get());
        return newlyLoaded.get();
    }

    @Override
    public SequenceStatus create (Router router) {
        if (null == router) {
            SequenceStatus.NULL_POINTER.logError("saveNew", "router");
            return SequenceStatus.NULL_POINTER;
        }
        long instanceId = router.getIdNE();
        if (instanceId != 0) {
            SequenceStatus.UNEXPECTED_FIELD_VALUE.logError("0", "idNE", "router");
            throw new IllegalArgumentException();
        }
        routerConfigurer.givePortsTo(router);
        List<RouterPort> routerPorts = router.getPorts();
        for (RouterPort routerPort : routerPorts) {
            portService.addToLoaded(routerPort);
        }
        addToLoaded(router);
        update(router);

        logger.info("Following ports are given to router with id {}:", router.getId());
        for (RouterPort port : router.getPorts()) {
            logger.info("Port with id {}", port.getId());
        }

        return SequenceStatus.OK;
    }

    @Override
    public Router update(Router router) {

        if (null == getLoaded(router.getIdNE())) {
            SequenceStatus.UNTRACKED_DB_OBJECT.logError("Router");
            loadedRouters.add(router);
        }
        Router savedRouter = routerRepository.save(router);

        if (savedRouter != router) {
            router.copyAutogeneratedValues(savedRouter);
        }
        return router;
    }

    @Override
    public void addToLoaded(Router instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("addToLoaded", "instance");
            throw new NullPointerException();
        }
        if (!loadedRouters.contains(instance)) {
            loadedRouters.add(instance);
        }
    }

    @Override
    protected void unload(Router instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("unload", "instance");
            throw new NullPointerException();
        }
        if (!loadedRouters.contains(instance)) {
            SequenceStatus.UNTRACKED_DB_OBJECT.logError("RouterService");
            return;
        }
        loadedRouters.remove(instance);
    }

    @Override
    protected void drop(Router instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("dropFromDB", "instance");
            throw new NullPointerException();
        }
        routerRepository.delete(instance);
    }

    @Override
    public SequenceStatus delete(Router instance) {
        throw new NotImplementedException();
    }

    public ResponseEntity setAddress (Long routerId, int portNumber, String ip, String mask) {
        Router router = getLoaded(routerId);
        if (null == router) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body(String.format("Router with id %d couldn't have been found.", routerId));
        }
        RouterPort port = router.getPort(portNumber);
        if (null == port) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body(String.format("Port with number %d couldn't have been found.", portNumber));
        }

        port.setAddress(ip, mask);
        routingTableService.routeDirectConnection(port);

        logger.info("RouterID {}, port {}, ip {}.", routerId, portNumber, router.getPort(portNumber).getIp());
        return ResponseEntity.ok("Ip set successfully.");
    }
}
