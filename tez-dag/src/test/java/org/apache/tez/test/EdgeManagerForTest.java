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

package org.apache.tez.test;

import java.util.List;

import org.apache.tez.dag.api.EdgeManager;
import org.apache.tez.dag.api.EdgeManagerContext;
import org.apache.tez.runtime.api.events.DataMovementEvent;
import org.apache.tez.runtime.api.events.InputFailedEvent;
import org.apache.tez.runtime.api.events.InputReadErrorEvent;

public class EdgeManagerForTest implements EdgeManager {

  private EdgeManagerContext edgeManagerContext = null;
  private boolean createdByFramework = true;

  public static EdgeManagerForTest createInstance() {
    EdgeManagerForTest e = new EdgeManagerForTest();
    e.createdByFramework = false;
    return e;
  }
  
  public boolean isCreatedByFramework() {
    return createdByFramework;
  }
  
  public EdgeManagerContext getEdgeManagerContext() {
    return edgeManagerContext;
  }

  
  
  // Overridden methods
  
  public EdgeManagerForTest() {
  }

  @Override
  public void initialize(EdgeManagerContext edgeManagerContext) {
    this.edgeManagerContext = edgeManagerContext;
  }

  @Override
  public int getNumDestinationTaskInputs(int numSourceTasks, int destinationTaskIndex) {
    return 0;
  }

  @Override
  public int getNumSourceTaskOutputs(int numDestinationTasks, int sourceTaskIndex) {
    return 0;
  }

  @Override
  public void routeEventToDestinationTasks(DataMovementEvent event, int sourceTaskIndex,
      int numDestinationTasks, List<Integer> taskIndices) {
  }

  @Override
  public void routeEventToDestinationTasks(InputFailedEvent event, int sourceTaskIndex,
      int numDestinationTasks, List<Integer> taskIndices) {
  }

  @Override
  public int getDestinationConsumerTaskNumber(int sourceTaskIndex, int numDestinationTasks) {
    return 0;
  }

  @Override
  public int routeEventToSourceTasks(int destinationTaskIndex, InputReadErrorEvent event) {
    return 0;
  }
  
  // End of overridden methods

}