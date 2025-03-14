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

package org.apache.druid.math.expr;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 */
public class Evals
{
  public static boolean isAllConstants(Expr... exprs)
  {
    return isAllConstants(Arrays.asList(exprs));
  }

  public static boolean isAllConstants(List<Expr> exprs)
  {
    for (Expr expr : exprs) {
      if (!(expr instanceof ConstantExpr)) {
        return false;
      }
    }
    return true;
  }

  public static long asLong(boolean x)
  {
    return x ? 1L : 0L;
  }

  public static double asDouble(boolean x)
  {
    return x ? 1D : 0D;
  }

  public static boolean asBoolean(long x)
  {
    return x > 0;
  }

  public static boolean asBoolean(double x)
  {
    return x > 0;
  }

  public static boolean asBoolean(@Nullable String x)
  {
    return Boolean.parseBoolean(x);
  }

  /**
   * Best effort try to turn a value into a boolean:
   *  {@link Boolean} will be passed directly through
   *  {@link String} will use {@link #asBoolean(String)}
   *  {@link Long} will use {@link #asBoolean(long)}
   *  {@link Number} will use {@link #asBoolean(double)}
   *  everything else, including null will be false
   */
  public static boolean objectAsBoolean(@Nullable Object val)
  {
    if (val instanceof Boolean) {
      return (Boolean) val;
    } else if (val instanceof String) {
      return Evals.asBoolean((String) val);
    } else if (val instanceof Long) {
      return Evals.asBoolean((Long) val);
    } else if (val instanceof Number) {
      return Evals.asBoolean(((Number) val).doubleValue());
    }
    return false;
  }

  /**
   * Call {@link Object#toString()} on a non-null value
   */
  @Nullable
  public static String asString(@Nullable Object o)
  {
    if (o == null) {
      return null;
    }
    return o.toString();
  }
}
