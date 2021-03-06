package dev.jarcadia.vimes;

import dev.jarcadia.redao.RedaoCommando;
import dev.jarcadia.retask.annontations.RetaskChangeHandler;
import dev.jarcadia.retask.annontations.RetaskParam;
import dev.jarcadia.vimes.model.Group;
import dev.jarcadia.vimes.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class StateWatcher {

    private final Logger logger = LoggerFactory.getLogger(StateWatcher.class);

    private final NotificationService notificationService;

    public StateWatcher(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RetaskChangeHandler(type = "instance", field = "state")
    public void instanceStateChanged(RedaoCommando rcommando, @RetaskParam("object") Instance instance,
            @RetaskParam("after") States.InstanceState state) {

        logger.info("Instance {} changed to {}", instance.getId(), state);
        Optional<Group> optGroup = instance.getGroup();
        if (optGroup.isPresent()) {
            Group group = optGroup.get();
            List<Instance> instances = group.getInstances();
            logger.info("Instance is part of group {} ({})", group.getId(), instances);

            int numInstances = group.getInstances().size();
            int numEnabled = (int) group.getInstances().stream()
                    .filter(inst -> States.InstanceState.Enabled == inst.getState())
                    .count();

            if (numEnabled == 0) {
                group.setState(States.GroupState.Down);
            } else if (numEnabled == numInstances) {
                group.setState(States.GroupState.Up);
            } else {
                group.setState(States.GroupState.Partial);
            }
        }
    }

    @RetaskChangeHandler(type = "group", field = "state")
    public void groupStateChanged(RedaoCommando rcommando, @RetaskParam("object") Group group,
            @RetaskParam("before") States.GroupState before, States.GroupState after) {
        logger.info("Group {} changed {} -> {}", group.getId(), before, after);
    }
}
