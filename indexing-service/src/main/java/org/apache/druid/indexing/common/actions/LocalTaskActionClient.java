/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.indexing.common.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.druid.indexing.common.task.IndexTaskUtils;
import org.apache.druid.indexing.common.task.Task;
import org.apache.druid.java.util.common.jackson.JacksonUtils;
import org.apache.druid.java.util.emitter.EmittingLogger;
import org.apache.druid.java.util.emitter.service.ServiceMetricEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LocalTaskActionClient implements TaskActionClient
{
  private static final EmittingLogger log = new EmittingLogger(LocalTaskActionClient.class);

  private final Task task;
  private final TaskActionToolbox toolbox;

  public LocalTaskActionClient(
      Task task,
      TaskActionToolbox toolbox
  )
  {
    this.task = task;
    this.toolbox = toolbox;
  }

  @Override
  public <RetType> RetType submit(TaskAction<RetType> taskAction)
  {
    log.debug("Performing action for task[%s]: %s", task.getId(), taskAction);
    final long performStartTime = System.currentTimeMillis();
    final RetType result = performAction(taskAction);
    emitTimerMetric("task/action/run/time", taskAction, System.currentTimeMillis() - performStartTime);
    return result;
  }

  private <R> R performAction(TaskAction<R> taskAction)
  {
    try {
      final R result;
      if (taskAction.canPerformAsync(task, toolbox)) {
        result = taskAction.performAsync(task, toolbox).get(5, TimeUnit.MINUTES);
      } else {
        result = taskAction.perform(task, toolbox);
      }

      return result;
    }
    catch (Throwable t) {
      log.error(t, "Failed to perform action[%s]", taskAction);
      throw new RuntimeException(t);
    }
  }

  private void emitTimerMetric(final String metric, final TaskAction<?> action, final long time)
  {
    final ServiceMetricEvent.Builder metricBuilder = ServiceMetricEvent.builder();
    IndexTaskUtils.setTaskDimensions(metricBuilder, task);
    final String actionType = getActionType(toolbox.getJsonMapper(), action);
    if (actionType != null) {
      metricBuilder.setDimension("taskActionType", actionType);
    }
    toolbox.getEmitter().emit(metricBuilder.setMetric(metric, Math.max(0, time)));
  }

  @Nullable
  static String getActionType(final ObjectMapper jsonMapper, final TaskAction<?> action)
  {
    try {
      final Map<String, Object> m = jsonMapper.convertValue(action, JacksonUtils.TYPE_REFERENCE_MAP_STRING_OBJECT);
      final Object typeObject = m.get(TaskAction.TYPE_FIELD);
      if (typeObject instanceof String) {
        return (String) typeObject;
      } else {
        return null;
      }
    }
    catch (Exception e) {
      return null;
    }
  }
}
