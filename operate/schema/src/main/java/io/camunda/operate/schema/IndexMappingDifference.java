/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema;

import static io.camunda.operate.schema.IndexMapping.IndexMappingProperty.createIndexMappingProperty;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexMappingDifference {

  private boolean equal;
  private Set<IndexMappingProperty> entriesOnlyOnLeft;
  private Set<IndexMappingProperty> entriesOnlyOnRight;
  private Set<IndexMappingProperty> entriesInCommon;
  private Set<PropertyDifference> entriesDiffering;

  public boolean isEqual() {
    return equal;
  }

  public IndexMappingDifference setEqual(final boolean equal) {
    this.equal = equal;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesOnlyOnLeft() {
    return entriesOnlyOnLeft;
  }

  public IndexMappingDifference setEntriesOnlyOnLeft(
      final Set<IndexMappingProperty> entriesOnlyOnLeft) {
    this.entriesOnlyOnLeft = entriesOnlyOnLeft;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesOnlyOnRight() {
    return entriesOnlyOnRight;
  }

  public IndexMappingDifference setEntriesOnlyOnRight(
      final Set<IndexMappingProperty> entriesOnlyOnRight) {
    this.entriesOnlyOnRight = entriesOnlyOnRight;
    return this;
  }

  public Set<IndexMappingProperty> getEntriesInCommon() {
    return entriesInCommon;
  }

  public IndexMappingDifference setEntriesInCommon(
      final Set<IndexMappingProperty> entriesInCommon) {
    this.entriesInCommon = entriesInCommon;
    return this;
  }

  public Set<PropertyDifference> getEntriesDiffering() {
    return entriesDiffering;
  }

  public IndexMappingDifference setEntriesDiffering(
      final Set<PropertyDifference> entriesDiffering) {
    this.entriesDiffering = entriesDiffering;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        equal, entriesOnlyOnLeft, entriesOnlyOnRight, entriesInCommon, entriesDiffering);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexMappingDifference that = (IndexMappingDifference) o;
    return equal == that.equal
        && Objects.equals(entriesOnlyOnLeft, that.entriesOnlyOnLeft)
        && Objects.equals(entriesOnlyOnRight, that.entriesOnlyOnRight)
        && Objects.equals(entriesInCommon, that.entriesInCommon)
        && Objects.equals(entriesDiffering, that.entriesDiffering);
  }

  @Override
  public String toString() {
    return "IndexMappingDifference{"
        + "equal="
        + equal
        + ", entriesOnlyOnLeft="
        + entriesOnlyOnLeft
        + ", entriesOnlyOnRight="
        + entriesOnlyOnRight
        + ", entriesInCommon="
        + entriesInCommon
        + ", entriesDiffering="
        + entriesDiffering
        + '}';
  }

  public static class IndexMappingDifferenceBuilder {
    private boolean ignoreDynamicDifferences;
    private IndexMapping left;
    private IndexMapping right;

    public static IndexMappingDifferenceBuilder builder() {
      return new IndexMappingDifferenceBuilder();
    }

    public IndexMappingDifferenceBuilder setLeft(final IndexMapping left) {
      this.left = left;
      return this;
    }

    public IndexMappingDifferenceBuilder setRight(final IndexMapping right) {
      this.right = right;
      return this;
    }

    public IndexMappingDifferenceBuilder setIgnoreDynamicDifferences(
        final boolean ignoreDynamicDifferences) {
      this.ignoreDynamicDifferences = ignoreDynamicDifferences;
      return this;
    }

    public IndexMappingDifference build() {
      final MapDifference<String, Object> difference = Maps.difference(left.toMap(), right.toMap());
      final IndexMappingDifference diff =
          new IndexMappingDifference()
              .setEqual(difference.areEqual())
              .setEntriesOnlyOnLeft(
                  difference.entriesOnlyOnLeft().entrySet().stream()
                      .map(p -> createIndexMappingProperty(p))
                      .collect(Collectors.toSet()))
              .setEntriesOnlyOnRight(
                  difference.entriesOnlyOnRight().entrySet().stream()
                      .map(p -> createIndexMappingProperty(p))
                      .collect(Collectors.toSet()))
              .setEntriesInCommon(
                  difference.entriesInCommon().entrySet().stream()
                      .map(p -> createIndexMappingProperty(p))
                      .collect(Collectors.toSet()));

      if (ignoreDynamicDifferences
          && (Boolean.parseBoolean(left.getDynamic())
              || Boolean.parseBoolean(right.getDynamic()))) {
        diff.setEntriesDiffering(Set.of());
      } else {
        diff.setEntriesDiffering(
            difference.entriesDiffering().entrySet().stream()
                .map(
                    entry ->
                        new PropertyDifference()
                            .setName(entry.getKey())
                            .setLeftValue(
                                new IndexMappingProperty()
                                    .setName(entry.getKey())
                                    .setTypeDefinition(entry.getValue().leftValue()))
                            .setRightValue(
                                new IndexMappingProperty()
                                    .setName(entry.getKey())
                                    .setTypeDefinition(entry.getValue().rightValue())))
                .collect(Collectors.toSet()));
      }

      return diff;
    }
  }

  public static class PropertyDifference {
    private String name;
    private IndexMappingProperty leftValue;
    private IndexMappingProperty rightValue;

    public String getName() {
      return name;
    }

    public PropertyDifference setName(final String name) {
      this.name = name;
      return this;
    }

    public IndexMappingProperty getLeftValue() {
      return leftValue;
    }

    public PropertyDifference setLeftValue(final IndexMappingProperty leftValue) {
      this.leftValue = leftValue;
      return this;
    }

    public IndexMappingProperty getRightValue() {
      return rightValue;
    }

    public PropertyDifference setRightValue(final IndexMappingProperty rightValue) {
      this.rightValue = rightValue;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, leftValue, rightValue);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final PropertyDifference that = (PropertyDifference) o;
      return Objects.equals(name, that.name)
          && Objects.equals(leftValue, that.leftValue)
          && Objects.equals(rightValue, that.rightValue);
    }

    @Override
    public String toString() {
      return "PropertyDifference{"
          + "name='"
          + name
          + '\''
          + ", leftValue="
          + leftValue
          + ", rightValue="
          + rightValue
          + '}';
    }
  }
}
