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

package org.apache.druid.segment.column;

import it.unimi.dsi.fastutil.Hash;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Comparator;

/**
 * Wrapper of {@link TypeStrategy} for nullable types, which stores {@link TypeStrategies#IS_NULL_BYTE} or
 * {@link TypeStrategies#IS_NOT_NULL_BYTE} in the leading byte of any value, as appropriate. If the value is null, only
 * {@link TypeStrategies#IS_NULL_BYTE} will be set, otherwise, the value bytes will be written after the null byte.
 *
 * layout: | null (byte) | value (byte[]) |
 *
 * This is not the most efficient way to track nulls, it is recommended to only use this wrapper if you MUST store null
 * values.
 *
 * @see TypeStrategy
 */
public final class NullableTypeStrategy<T> implements Comparator<T>, Hash.Strategy<T>
{
  private final TypeStrategy<T> delegate;
  private final Comparator<T> delegateComparator;

  public NullableTypeStrategy(TypeStrategy<T> delegate)
  {
    this.delegate = delegate;
    this.delegateComparator = Comparator.nullsFirst(delegate::compare);
  }

  public int estimateSizeBytes(@Nullable T value)
  {
    if (value == null) {
      return Byte.BYTES;
    }
    return Byte.BYTES + delegate.estimateSizeBytes(value);
  }


  @Nullable
  public T read(ByteBuffer buffer)
  {
    if ((buffer.get() & TypeStrategies.IS_NULL_BYTE) == TypeStrategies.IS_NULL_BYTE) {
      return null;
    }
    return delegate.read(buffer);
  }

  @CheckReturnValue
  public int write(ByteBuffer buffer, @Nullable T value, int maxSizeBytes)
  {
    final int max = Math.min(buffer.limit() - buffer.position(), maxSizeBytes);
    final int remaining = max - Byte.BYTES;
    if (remaining >= 0) {
      // if we have room left, write the null byte and the value
      if (value == null) {
        buffer.put(TypeStrategies.IS_NULL_BYTE);
        return Byte.BYTES;
      }
      buffer.put(TypeStrategies.IS_NOT_NULL_BYTE);
      int written = delegate.write(buffer, value, maxSizeBytes - Byte.BYTES);
      return written < 0 ? written : Byte.BYTES + written;
    } else {
      if (value == null) {
        return remaining;
      }
      // call delegate.write anyway to get the total amount of extra space needed to serialize the value
      return remaining + delegate.write(buffer, value, 0);
    }
  }

  @Nullable
  public T read(ByteBuffer buffer, int offset)
  {
    final int oldPosition = buffer.position();
    try {
      buffer.position(offset);
      T value = read(buffer);
      return value;
    }
    finally {
      buffer.position(oldPosition);
    }
  }

  /**
   * Whether the {@link #read} methods return an object that may retain a reference to the underlying memory of the
   * provided {@link ByteBuffer}. If a reference is sometimes retained, this method returns true. It returns false if,
   * and only if, a reference is *never* retained.
   * <p>
   * If this method returns true, and the caller does not control the lifecycle of the underlying memory or cannot
   * ensure that it will not change over the lifetime of the returned object, callers should copy the memory to a new
   * location that they do control the lifecycle of and will be available for the duration of the returned object.
   */
  public boolean readRetainsBufferReference()
  {
    return delegate.readRetainsBufferReference();
  }

  @CheckReturnValue
  public int write(ByteBuffer buffer, int offset, @Nullable T value, int maxSizeBytes)
  {
    final int oldPosition = buffer.position();
    try {
      buffer.position(offset);
      return write(buffer, value, maxSizeBytes);
    }
    finally {
      buffer.position(oldPosition);
    }
  }

  @Override
  public int compare(@Nullable T o1, @Nullable T o2)
  {
    return delegateComparator.compare(o1, o2);
  }

  public boolean groupable()
  {
    return delegate.groupable();
  }

  @Override
  public int hashCode(@Nullable T o)
  {
    return o == null ? 0 : delegate.hashCode(o);
  }

  @Override
  public boolean equals(@Nullable T a, @Nullable T b)
  {
    if (a == null) {
      return b == null;
    }
    return b != null && delegate.equals(a, b);
  }

  public Class<?> getClazz()
  {
    return delegate.getClazz();
  }
}
