package io.camunda.zeebe.gateway.validation;

import io.camunda.zeebe.gateway.validation.model.BranchDescriptor;
import io.camunda.zeebe.gateway.validation.model.EnumLiteral;
import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import io.camunda.zeebe.gateway.validation.model.PatternDescriptor;
import io.camunda.zeebe.gateway.validation.spi.GroupDescriptorProvider;
import java.util.regex.Pattern;

/** Test-only provider supplying synthetic groups for enum/pattern & specificity tests. */
public final class TestGroupDescriptorProvider implements GroupDescriptorProvider {
  private static final GroupDescriptor ENUM_PATTERN_GROUP;
  private static final GroupDescriptor SPECIFICITY_GROUP;
  private static final GroupDescriptor EQUAL_SPECIFICITY_GROUP;
  static {
    // EnumPatternGroup: one branch with required 'kind' (enum A,B) and optional 'value' (digits pattern)
    ENUM_PATTERN_GROUP = new GroupDescriptor(
        "EnumPatternGroup",
        new BranchDescriptor[]{
            new BranchDescriptor(
                0,
                1,
                new String[]{"kind"},
                new String[]{"value"},
                new EnumLiteral[][]{
                    new EnumLiteral[]{new EnumLiteral("A"), new EnumLiteral("B")}, // kind enums
                    new EnumLiteral[0] // value
                },
                new PatternDescriptor[]{
                    new PatternDescriptor("value", Pattern.compile("^[0-9]+$"))
                })
        });

    // SpecificityGroup: branch0 requires kind; branch1 requires kind & value (higher specificity)
    SPECIFICITY_GROUP = new GroupDescriptor(
        "SpecificityGroup",
        new BranchDescriptor[]{
            new BranchDescriptor(0, 1, new String[]{"kind"}, new String[0], new EnumLiteral[0][], new PatternDescriptor[0]),
            new BranchDescriptor(1, 2, new String[]{"kind", "value"}, new String[0], new EnumLiteral[0][], new PatternDescriptor[0])
        });

  // EqualSpecificityGroup: two branches with same specificity (1 required each) to trigger ambiguity
  EQUAL_SPECIFICITY_GROUP = new GroupDescriptor(
    "EqualSpecificityGroup",
    new BranchDescriptor[]{
      new BranchDescriptor(0, 1, new String[]{"a"}, new String[0], new EnumLiteral[0][], new PatternDescriptor[0]),
      new BranchDescriptor(1, 1, new String[]{"b"}, new String[0], new EnumLiteral[0][], new PatternDescriptor[0])
    });
  }

  @Override
  public GroupDescriptor find(String groupId) {
    return switch (groupId) {
      case "EnumPatternGroup" -> ENUM_PATTERN_GROUP;
      case "SpecificityGroup" -> SPECIFICITY_GROUP;
  case "EqualSpecificityGroup" -> EQUAL_SPECIFICITY_GROUP;
      default -> null;
    };
  }
}
