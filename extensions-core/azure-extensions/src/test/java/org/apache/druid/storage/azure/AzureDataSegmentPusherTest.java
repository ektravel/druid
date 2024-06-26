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

package org.apache.druid.storage.azure;

import com.azure.storage.blob.models.BlobStorageException;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.java.util.common.MapUtils;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.timeline.DataSegment;
import org.apache.druid.timeline.partition.LinearShardSpec;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AzureDataSegmentPusherTest extends EasyMockSupport
{
  private static final String ACCOUNT = "account";
  private static final String CONTAINER_NAME = "container";
  private static final String PREFIX = "prefix";
  private static final String BLOB_PATH = " Россия 한국 中国!?/2015-04-12T00:00:00.000Z_2015-04-13T00:00:00.000Z/1/0/index.zip";
  private static final DataSegment DATA_SEGMENT = new DataSegment(
      " Россия 한국 中国!?",
      Intervals.of("2015-04-12/2015-04-13"),
      "1",
      ImmutableMap.of("containerName", CONTAINER_NAME, "blobPath", BLOB_PATH),
      null,
      null,
      new LinearShardSpec(0),
      0,
      1
  );
  private static final byte[] DATA = new byte[]{0x0, 0x0, 0x0, 0x1};
  private static final String UNIQUE_MATCHER_NO_PREFIX = "foo/20150101T000000\\.000Z_20160101T000000\\.000Z/0/0/[A-Za-z0-9-]{36}/index\\.zip";
  private static final String UNIQUE_MATCHER_PREFIX = PREFIX + "/" + UNIQUE_MATCHER_NO_PREFIX;
  private static final String NON_UNIQUE_NO_PREFIX_MATCHER = "foo/20150101T000000\\.000Z_20160101T000000\\.000Z/0/0/index\\.zip";
  private static final String NON_UNIQUE_WITH_PREFIX_MATCHER = PREFIX + "/" + "foo/20150101T000000\\.000Z_20160101T000000\\.000Z/0/0/index\\.zip";
  private static final int MAX_TRIES = 3;

  private static final DataSegment SEGMENT_TO_PUSH = new DataSegment(
      "foo",
      Intervals.of("2015/2016"),
      "0",
      new HashMap<>(),
      new ArrayList<>(),
      new ArrayList<>(),
      new LinearShardSpec(0),
      0,
      DATA.length
  );

  private AzureStorage azureStorage;
  private AzureAccountConfig azureAccountConfig;
  private AzureDataSegmentConfig segmentConfigWithPrefix;
  private AzureDataSegmentConfig segmentConfigWithoutPrefix;

  @BeforeEach
  public void before()
  {
    azureStorage = createMock(AzureStorage.class);
    azureAccountConfig = new AzureAccountConfig();
    azureAccountConfig.setMaxTries(MAX_TRIES);
    azureAccountConfig.setAccount(ACCOUNT);

    segmentConfigWithPrefix = new AzureDataSegmentConfig();
    segmentConfigWithPrefix.setContainer(CONTAINER_NAME);
    segmentConfigWithPrefix.setPrefix(PREFIX + "/");

    segmentConfigWithoutPrefix = new AzureDataSegmentConfig();
    segmentConfigWithoutPrefix.setContainer(CONTAINER_NAME);
  }

  @Test
  public void test_push_nonUniquePathNoPrefix_succeeds(@TempDir Path tempPath) throws Exception
  {
    boolean useUniquePath = false;
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithoutPrefix
    );

    // Create a mock segment on disk
    File tmp = tempPath.resolve("version.bin").toFile();
    Files.write(DATA, tmp);

    String azurePath = pusher.getAzurePath(SEGMENT_TO_PUSH, useUniquePath);
    azureStorage.uploadBlockBlob(EasyMock.anyObject(File.class), EasyMock.eq(CONTAINER_NAME), EasyMock.eq(azurePath), EasyMock.eq(MAX_TRIES));
    EasyMock.expectLastCall();

    replayAll();

    DataSegment segment = pusher.push(tempPath.toFile(), SEGMENT_TO_PUSH, useUniquePath);

    assertTrue(
        Pattern.compile(NON_UNIQUE_NO_PREFIX_MATCHER)
               .matcher(segment.getLoadSpec().get("blobPath").toString())
               .matches(),
        segment.getLoadSpec().get("blobPath").toString()
    );

    assertEquals(SEGMENT_TO_PUSH.getSize(), segment.getSize());

    verifyAll();
  }

  @Test
  public void test_push_nonUniquePathWithPrefix_succeeds(@TempDir Path tempPath) throws Exception
  {
    boolean useUniquePath = false;
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix
    );

    // Create a mock segment on disk
    File tmp = tempPath.resolve("version.bin").toFile();
    Files.write(DATA, tmp);

    String azurePath = pusher.getAzurePath(SEGMENT_TO_PUSH, useUniquePath);
    azureStorage.uploadBlockBlob(
        EasyMock.anyObject(File.class),
        EasyMock.eq(CONTAINER_NAME),
        EasyMock.eq(PREFIX + "/" + azurePath),
        EasyMock.eq(MAX_TRIES)
    );
    EasyMock.expectLastCall();

    replayAll();

    DataSegment segment = pusher.push(tempPath.toFile(), SEGMENT_TO_PUSH, useUniquePath);

    assertTrue(Pattern.compile(NON_UNIQUE_WITH_PREFIX_MATCHER).matcher(segment.getLoadSpec().get("blobPath").toString()).matches(),
                          segment.getLoadSpec().get("blobPath").toString());

    assertEquals(SEGMENT_TO_PUSH.getSize(), segment.getSize());

    verifyAll();
  }

  @Test
  public void test_push_uniquePathNoPrefix_succeeds(@TempDir Path tempPath) throws Exception
  {
    boolean useUniquePath = true;
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithoutPrefix);

    // Create a mock segment on disk
    File tmp = tempPath.resolve("version.bin").toFile();

    Files.write(DATA, tmp);

    String azurePath = pusher.getAzurePath(SEGMENT_TO_PUSH, useUniquePath);
    azureStorage.uploadBlockBlob(
        EasyMock.anyObject(File.class),
        EasyMock.eq(CONTAINER_NAME),
        EasyMock.matches(UNIQUE_MATCHER_NO_PREFIX),
        EasyMock.eq(MAX_TRIES)
    );
    EasyMock.expectLastCall();

    replayAll();

    DataSegment segment = pusher.push(tempPath.toFile(), SEGMENT_TO_PUSH, useUniquePath);

    assertTrue(
        Pattern.compile(UNIQUE_MATCHER_NO_PREFIX)
               .matcher(segment.getLoadSpec().get("blobPath").toString())
               .matches(),
        segment.getLoadSpec().get("blobPath").toString());

    assertEquals(SEGMENT_TO_PUSH.getSize(), segment.getSize());

    verifyAll();
  }

  @Test
  public void test_push_uniquePath_succeeds(@TempDir Path tempPath) throws Exception
  {
    boolean useUniquePath = true;
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);

    // Create a mock segment on disk
    File tmp = tempPath.resolve("version.bin").toFile();

    Files.write(DATA, tmp);

    String azurePath = pusher.getAzurePath(SEGMENT_TO_PUSH, useUniquePath);
    azureStorage.uploadBlockBlob(
        EasyMock.anyObject(File.class),
        EasyMock.eq(CONTAINER_NAME),
        EasyMock.matches(UNIQUE_MATCHER_PREFIX),
        EasyMock.eq(MAX_TRIES)
    );
    EasyMock.expectLastCall();

    replayAll();

    DataSegment segment = pusher.push(tempPath.toFile(), SEGMENT_TO_PUSH, useUniquePath);

    assertTrue(
        Pattern.compile(UNIQUE_MATCHER_PREFIX)
               .matcher(segment.getLoadSpec().get("blobPath").toString())
               .matches(),
        segment.getLoadSpec().get("blobPath").toString()
    );

    assertEquals(SEGMENT_TO_PUSH.getSize(), segment.getSize());

    verifyAll();
  }

  @Test
  public void test_push_exception_throwsException(@TempDir Path tempPath) throws Exception
  {
    boolean useUniquePath = true;
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);

    // Create a mock segment on disk
    File tmp = tempPath.resolve("version.bin").toFile();

    Files.write(DATA, tmp);

    azureStorage.uploadBlockBlob(EasyMock.anyObject(File.class), EasyMock.eq(CONTAINER_NAME), EasyMock.anyString(), EasyMock.eq(MAX_TRIES));
    EasyMock.expectLastCall().andThrow(new BlobStorageException("", null, null));

    replayAll();

    assertThrows(
        RuntimeException.class,
        () -> pusher.push(tempPath.toFile(), SEGMENT_TO_PUSH, useUniquePath)
    );

    verifyAll();
  }

  @Test
  public void getAzurePathsTest()
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);
    final String storageDir = pusher.getStorageDir(DATA_SEGMENT, false);
    final String azurePath = pusher.getAzurePath(DATA_SEGMENT, false);

    assertEquals(
        StringUtils.format("%s/%s", storageDir, AzureStorageDruidModule.INDEX_ZIP_FILE_NAME),
        azurePath
    );
  }

  @Test
  public void uploadDataSegmentTest() throws BlobStorageException, IOException
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);
    final int binaryVersion = 9;
    final File compressedSegmentData = new File("index.zip");
    final String azurePath = pusher.getAzurePath(DATA_SEGMENT, false);

    azureStorage.uploadBlockBlob(compressedSegmentData, CONTAINER_NAME, azurePath, MAX_TRIES);
    EasyMock.expectLastCall();

    replayAll();

    DataSegment pushedDataSegment = pusher.uploadDataSegment(
        DATA_SEGMENT,
        binaryVersion,
        0, // empty file
        compressedSegmentData,
        azurePath
    );

    assertEquals(compressedSegmentData.length(), pushedDataSegment.getSize());
    assertEquals(binaryVersion, (int) pushedDataSegment.getBinaryVersion());
    Map<String, Object> loadSpec = pushedDataSegment.getLoadSpec();
    assertEquals(AzureStorageDruidModule.SCHEME, MapUtils.getString(loadSpec, "type"));
    assertEquals(azurePath, MapUtils.getString(loadSpec, "blobPath"));

    verifyAll();
  }

  @Test
  public void getPathForHadoopWithPrefixTest()
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);
    String hadoopPath = pusher.getPathForHadoop();
    assertEquals("wasbs://container@account.blob.core.windows.net/prefix/", hadoopPath);
  }

  @Test
  public void getPathForHadoopWithoutPrefixTest()
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithoutPrefix);
    String hadoopPath = pusher.getPathForHadoop();
    assertEquals("wasbs://container@account.blob.core.windows.net/", hadoopPath);
  }

  @Test
  public void test_getAllowedPropertyPrefixesForHadoop_returnsExpcetedPropertyPrefixes()
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);
    List<String> actualPropertyPrefixes = pusher.getAllowedPropertyPrefixesForHadoop();
    assertEquals(AzureDataSegmentPusher.ALLOWED_PROPERTY_PREFIXES_FOR_HADOOP, actualPropertyPrefixes);
  }

  @Test
  public void storageDirContainsNoColonsTest()
  {
    AzureDataSegmentPusher pusher = new AzureDataSegmentPusher(azureStorage, azureAccountConfig, segmentConfigWithPrefix);
    DataSegment withColons = DATA_SEGMENT.withVersion("2018-01-05T14:54:09.295Z");
    String segmentPath = pusher.getStorageDir(withColons, false);
    assertFalse(segmentPath.contains(":"), "Path should not contain any columns");
  }
}
