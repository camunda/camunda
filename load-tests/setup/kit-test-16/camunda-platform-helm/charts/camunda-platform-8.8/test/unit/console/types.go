package console

type ConsoleYAML struct {
	Camunda CamundaConfig `yaml:"camunda"`
}

type CamundaConfig struct {
	Console ConsoleConfig `yaml:"console"`
}

type ConsoleConfig struct {
	OAuth OAuth2Config `yaml:"oAuth"`
}

type OAuth2Config struct {
	ClientId string `yaml:"clientId"`
	Type     string `yaml:"type"`
	Audience string `yaml:"audience"`
	JwksUri  string `yaml:"jwksUri"`
}
