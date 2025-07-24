/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api.coverage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collection;

/** A suite includes several Runs and contains the data for the coverage calculation. */
public class Suite {

  /** The id of the suite */
  private final String id;

  /** The name of the suite */
  private final String name;

  /** List of runs that are in the suite */
  private final Collection<Run> runs;

  public Suite(final String id, final String name) {
    this(id, name, new ArrayList<>());
  }

  @JsonCreator
  public Suite(
      @JsonProperty("id") final String id,
      @JsonProperty("name") final String name,
      @JsonProperty("runs") final Collection<Run> runs) {
    this.id = id;
    this.name = name;
    this.runs = runs;
  }

  /** Adds a Run to the suite */
  public void addRun(final Run run) {
    runs.add(run);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Collection<Run> getRuns() {
    return runs;
  }
}
