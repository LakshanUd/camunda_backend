package com.example.camunda_backend.config;

import com.example.camunda_backend.listener.DynamicTaskAssignmentListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GlobalTaskListenerPlugin extends AbstractProcessEnginePlugin {

    private final DynamicTaskAssignmentListener dynamicListener;

    public GlobalTaskListenerPlugin(DynamicTaskAssignmentListener dynamicListener) {
        this.dynamicListener = dynamicListener;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        // Retrieve or initialize the list of parse listeners
        List<BpmnParseListener> preParseListeners = processEngineConfiguration.getCustomPreBPMNParseListeners();
        if (preParseListeners == null) {
            preParseListeners = new ArrayList<>();
            processEngineConfiguration.setCustomPreBPMNParseListeners(preParseListeners);
        }

        // Add our custom listener that fires when Camunda reads a BPMN file
        preParseListeners.add(new AbstractBpmnParseListener() {
            @Override
            public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
                TaskDefinition taskDefinition = ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition();
                
                // Automatically inject our DynamicTaskAssignmentListener into the "create" event of every user task!
                taskDefinition.addTaskListener(org.camunda.bpm.engine.delegate.TaskListener.EVENTNAME_CREATE, dynamicListener);
                
                System.out.println("[SYSTEM] Attached dynamic router to task: " + taskDefinition.getKey());
            }
        });
    }
}