package connectors

import "testing"

func TestUsePropertiesLauncher(t *testing.T) {
	cases := []struct {
		version string
		expect  bool
	}{
		{"8.9.0-SNAPSHOT", true},
		{"8.10.0", true},
		{"9.0.0-alpha1", true},
		{"8.8.5", false},
		{"7.17.0", false},
		{"invalid", false},
		{"", false},
	}

	for _, tc := range cases {
		t.Run(tc.version, func(t *testing.T) {
			if UsePropertiesLauncher(tc.version) != tc.expect {
				t.Fatalf("expected %v for version %s", tc.expect, tc.version)
			}
		})
	}
}
