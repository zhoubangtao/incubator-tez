/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tez.runtime.library.broadcast.input;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.SerializationFactory;
import org.apache.tez.runtime.library.api.KeyValueReader;
import org.apache.tez.runtime.library.common.ConfigUtils;
import org.apache.tez.runtime.library.common.shuffle.impl.InMemoryReader;
import org.apache.tez.runtime.library.common.sort.impl.IFile;
import org.apache.tez.runtime.library.shuffle.common.FetchedInput;
import org.apache.tez.runtime.library.shuffle.common.FetchedInput.Type;
import org.apache.tez.runtime.library.shuffle.common.MemoryFetchedInput;

public class BroadcastKVReader<K, V> implements KeyValueReader {

  private static final Log LOG = LogFactory.getLog(BroadcastKVReader.class);
  
  private final BroadcastShuffleManager shuffleManager;
  private final CompressionCodec codec;
  
  private final Class<K> keyClass;
  private final Class<V> valClass;
  private final Deserializer<K> keyDeserializer;
  private final Deserializer<V> valDeserializer;
  private final DataInputBuffer keyIn;
  private final DataInputBuffer valIn;

  private final boolean ifileReadAhead;
  private final int ifileReadAheadLength;
  private final int ifileBufferSize;
  
  private K key;
  private V value;
  
  private FetchedInput currentFetchedInput;
  private IFile.Reader currentReader;
  
  private int numRecordsRead = 0;
  
  public BroadcastKVReader(BroadcastShuffleManager shuffleManager, Configuration conf,
      CompressionCodec codec, boolean ifileReadAhead, int ifileReadAheadLength, int ifileBufferSize)
      throws IOException {
    this.shuffleManager = shuffleManager;

    this.codec = codec;
    this.ifileReadAhead = ifileReadAhead;
    this.ifileReadAheadLength = ifileReadAheadLength;
    this.ifileBufferSize = ifileBufferSize;

    this.keyClass = ConfigUtils.getIntermediateInputKeyClass(conf);
    this.valClass = ConfigUtils.getIntermediateInputValueClass(conf);

    this.keyIn = new DataInputBuffer();
    this.valIn = new DataInputBuffer();

    SerializationFactory serializationFactory = new SerializationFactory(conf);

    this.keyDeserializer = serializationFactory.getDeserializer(keyClass);
    this.keyDeserializer.open(keyIn);
    this.valDeserializer = serializationFactory.getDeserializer(valClass);
    this.valDeserializer.open(valIn);
  }

  // TODO NEWTEZ Maybe add an interface to check whether next will block.
  
  /**
   * Moves to the next key/values(s) pair
   * 
   * @return true if another key/value(s) pair exists, false if there are no
   *         more.
   * @throws IOException
   *           if an error occurs
   */
  @Override  
  public boolean next() throws IOException {
    if (readNextFromCurrentReader()) {
      numRecordsRead++;
      return true;
    } else {
      boolean nextInputExists = moveToNextInput();
      while (nextInputExists) {
        if(readNextFromCurrentReader()) {
          numRecordsRead++;
          return true;
        }
        nextInputExists = moveToNextInput();
      }
      LOG.info("Num Records read: " + numRecordsRead);
      return false;
    }
  }

  @Override
  public Object getCurrentKey() throws IOException {
    return (Object) key;
  }

  @Override
  public Object getCurrentValue() throws IOException {
    return value;
  }

  /**
   * Tries reading the next key and value from the current reader.
   * @return true if the current reader has more records
   * @throws IOException
   */
  private boolean readNextFromCurrentReader() throws IOException {
    // Initial reader.
    if (this.currentReader == null) {
      return false;
    } else {
      boolean hasMore = this.currentReader.nextRawKey(keyIn);
      if (hasMore) {
        this.currentReader.nextRawValue(valIn);
        this.key = keyDeserializer.deserialize(this.key);
        this.value = valDeserializer.deserialize(this.value);
        return true;
      }
      return false;
    }
  }
  
  /**
   * Moves to the next available input. This method may block if the input is not ready yet.
   * Also takes care of closing the previous input.
   * 
   * @return true if the next input exists, false otherwise
   * @throws IOException
   * @throws InterruptedException
   */
  private boolean moveToNextInput() throws IOException {
    if (currentReader != null) { // Close the current reader.
      currentReader.close();
      currentFetchedInput.free();
    }
    try {
      currentFetchedInput = shuffleManager.getNextInput();
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for next available input", e);
      throw new IOException(e);
    }
    if (currentFetchedInput == null) {
      return false; // No more inputs
    } else {
      currentReader = openIFileReader(currentFetchedInput);
      return true;
    }
  }

  public IFile.Reader openIFileReader(FetchedInput fetchedInput)
      throws IOException {
    if (fetchedInput.getType() == Type.MEMORY) {
      MemoryFetchedInput mfi = (MemoryFetchedInput) fetchedInput;

      return new InMemoryReader(null, mfi.getInputAttemptIdentifier(),
          mfi.getBytes(), 0, (int) mfi.getActualSize());
    } else {
      return new IFile.Reader(fetchedInput.getInputStream(),
          fetchedInput.getCompressedSize(), codec, null, ifileReadAhead,
          ifileReadAheadLength, ifileBufferSize);
    }
  }
}
