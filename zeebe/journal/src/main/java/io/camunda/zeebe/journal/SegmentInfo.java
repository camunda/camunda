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
package io.camunda.zeebe.journal;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

/**
 * Information about segments of a journal.
 *
 * @param segmentPaths The paths to the segment files.
 * @param firstAsqn The first ASQN in the segments. Empty if no record with an ASQN exists.
 */
public record SegmentInfo(Collection<Path> segmentPaths, OptionalLong firstAsqn) {

  /** An empty result with no segments and no first ASQN. */
  public static final SegmentInfo EMPTY = new SegmentInfo(List.of(), OptionalLong.empty());
}
