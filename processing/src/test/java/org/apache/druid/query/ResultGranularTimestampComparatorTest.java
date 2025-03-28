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

package org.apache.druid.query;

import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

/**
 */
@RunWith(Parameterized.class)
public class ResultGranularTimestampComparatorTest
{
  @Parameterized.Parameters(name = "descending={0}")
  public static Iterable<Object[]> constructorFeeder()
  {
    return QueryRunnerTestHelper.transformToConstructionFeeder(Arrays.asList(false, true));
  }

  private final boolean descending;

  public ResultGranularTimestampComparatorTest(boolean descending)
  {
    this.descending = descending;
  }

  private final DateTime time = DateTimes.of("2011-11-11");

  @Test
  public void testCompareAll()
  {
    Result<Object> r1 = new Result<>(time, null);
    Result<Object> r2 = new Result<>(time.plusYears(5), null);

    Assert.assertEquals(ResultGranularTimestampComparator.create(Granularities.ALL, descending).compare(r1, r2), 0);
  }

  @Test
  public void testCompareDay()
  {
    Result<Object> res = new Result<>(time, null);
    Result<Object> same = new Result<>(time.plusHours(12), null);
    Result<Object> greater = new Result<>(time.plusHours(25), null);
    Result<Object> less = new Result<>(time.minusHours(1), null);

    Granularity day = Granularities.DAY;
    Assert.assertEquals(ResultGranularTimestampComparator.create(day, descending).compare(res, same), 0);
    Assert.assertEquals(ResultGranularTimestampComparator.create(day, descending).compare(res, greater), descending ? 1 : -1);
    Assert.assertEquals(ResultGranularTimestampComparator.create(day, descending).compare(res, less), descending ? -1 : 1);
  }
  
  @Test
  public void testCompareHour()
  {
    Result<Object> res = new Result<>(time, null);
    Result<Object> same = new Result<>(time.plusMinutes(55), null);
    Result<Object> greater = new Result<>(time.plusHours(1), null);
    Result<Object> less = new Result<>(time.minusHours(1), null);

    Granularity hour = Granularities.HOUR;
    Assert.assertEquals(ResultGranularTimestampComparator.create(hour, descending).compare(res, same), 0);
    Assert.assertEquals(ResultGranularTimestampComparator.create(hour, descending).compare(res, greater), descending ? 1 : -1);
    Assert.assertEquals(ResultGranularTimestampComparator.create(hour, descending).compare(res, less), descending ? -1 : 1);
  }
}
