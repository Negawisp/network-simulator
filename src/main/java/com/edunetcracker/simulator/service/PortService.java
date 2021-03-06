package com.edunetcracker.simulator.service;

import com.edunetcracker.simulator.database.repository.portRepository.RouterPortRepository;
import com.edunetcracker.simulator.database.repository.portRepository.SwitchPortRepository;
import com.edunetcracker.simulator.model.port.Port;
import com.edunetcracker.simulator.model.port.RouterPort;
import com.edunetcracker.simulator.model.port.SwitchPort;
import com.edunetcracker.simulator.service.status.SequenceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.validation.UnexpectedTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PortService extends DBService<Port> {

    @Autowired
    private RouterPortRepository routerPortRepository;
    @Autowired
    private SwitchPortRepository switchPortRepository;

    private List<Port> loadedPorts = new ArrayList<>();


    @Override
    public void addToLoaded(Port instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("addToLoaded", "instance");
            throw new NullPointerException();
        }
        if (loadedPorts.contains(instance)) {
            return;
        }
        loadedPorts.add(instance);
    }

    @Override
    public void unload(Port instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("unload", "instance");
            throw new NullPointerException();
        }
        if (!loadedPorts.contains(instance)) {
            SequenceStatus.UNTRACKED_DB_OBJECT.logError("PortService");
            return;
        }
        loadedPorts.remove(instance);
    }

    @Override
    public Port getLoaded(long id) {
        for (Port table : loadedPorts) {
            if (table.getId() == id) {
                return table;
            }
        }
        SequenceStatus.UNTRACKED_DB_OBJECT.logError("Port");
        throw new ArrayStoreException(SequenceStatus.UNTRACKED_DB_OBJECT.getStringBody("Port"));
    }

    @Override
    public Port get(long id) {
        Port loadedPort = getLoaded(id);
        if (null != loadedPort) {
            return loadedPort;
        }
        Optional<RouterPort> newlyLoadedRP = routerPortRepository.findById(id);
        Optional<SwitchPort> newlyLoadedSP = switchPortRepository.findById(id);
        Port newlyLoadedPort = null;
        if (newlyLoadedRP.isPresent()) {
            newlyLoadedPort = newlyLoadedRP.get();
        }
        if (newlyLoadedSP.isPresent()) {
            if (null != newlyLoadedPort) {
                SequenceStatus.DUPLICATE_ID.logError("Port", id);
                throw new ArrayStoreException();
            }
            newlyLoadedPort = newlyLoadedSP.get();
        }
        if (null == newlyLoadedPort) {
            SequenceStatus.NOT_FOUND_IN_DATABASE.logWarning("Port", id);
        }
        return newlyLoadedPort;
    }

    @Override
    public SequenceStatus create(Port instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("saveNew", "instance");
            throw new NullPointerException();
        }
        long instanceId = instance.getId();
        if (instanceId != 0) {
            SequenceStatus.UNEXPECTED_FIELD_VALUE.logError("0", "ID", "Port");
            return SequenceStatus.UNEXPECTED_FIELD_VALUE;
        }
        loadedPorts.add(instance);
        update(instance);
        return SequenceStatus.OK;
    }

    @Override
    public Port update(Port instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("updateInDB", "Port instance");
            throw new NullPointerException();
        }
        if (null == getLoaded(instance.getId())) {
            SequenceStatus.UNTRACKED_DB_OBJECT.logError("Port");
            loadedPorts.add(instance);
        }
        Port savedPort = null;
        if (instance instanceof RouterPort) {
            savedPort = routerPortRepository.save((RouterPort)instance);
        }
        if (instance instanceof SwitchPort) {
            savedPort = switchPortRepository.save((SwitchPort)instance);
        }
        if (savedPort == null) {
            SequenceStatus.PARAMETER_TYPE_INCONSISTENCY.logError("Port");
            throw new UnexpectedTypeException();
        }
        if (savedPort != instance) {
            instance.copyAutogeneratedValues(savedPort);
        }
        return instance;
    }

    @Override
    public void drop(Port instance) {
        if (null == instance) {
            SequenceStatus.NULL_POINTER.logError("dropFromDB", "instance");
            throw new NullPointerException();
        }
        if (instance instanceof RouterPort) {
            routerPortRepository.delete((RouterPort)instance);
        }
        if (instance instanceof SwitchPort) {
            switchPortRepository.delete((SwitchPort)instance);
        }
    }

    @Override
    public SequenceStatus delete(Port instance) {
        throw new NotImplementedException();
    }
}
