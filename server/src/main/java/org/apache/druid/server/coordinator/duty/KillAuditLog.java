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

package org.apache.druid.server.coordinator.duty;

import com.google.inject.Inject;
import org.apache.druid.audit.AuditManager;
import org.apache.druid.server.coordinator.DruidCoordinatorConfig;
import org.apache.druid.server.coordinator.stats.Stats;
import org.joda.time.DateTime;

public class KillAuditLog extends MetadataCleanupDuty
{
  private final AuditManager auditManager;

  @Inject
  public KillAuditLog(
      AuditManager auditManager,
      DruidCoordinatorConfig config
  )
  {
    super(
        "audit logs",
        "druid.coordinator.kill.audit",
        config.getCoordinatorAuditKillPeriod(),
        config.getCoordinatorAuditKillDurationToRetain(),
        Stats.Kill.AUDIT_LOGS,
        config
    );
    this.auditManager = auditManager;
  }

  @Override
  protected int cleanupEntriesCreatedBefore(DateTime minCreatedTime)
  {
    return auditManager.removeAuditLogsOlderThan(minCreatedTime.getMillis());
  }
}
