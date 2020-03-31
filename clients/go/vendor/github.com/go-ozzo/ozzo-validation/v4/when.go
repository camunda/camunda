package validation

// When returns a validation rule that executes the given list of rules when the condition is true.
func When(condition bool, rules ...Rule) WhenRule {
	return WhenRule{
		condition: condition,
		rules:     rules,
	}
}

// WhenRule is a validation rule that executes the given list of rules when the condition is true.
type WhenRule struct {
	condition bool
	rules     []Rule
}

// Validate checks if the condition is true and if so, it validates the value using the specified rules.
func (r WhenRule) Validate(value interface{}) error {
	if r.condition {
		return Validate(value, r.rules...)
	}

	return nil
}
