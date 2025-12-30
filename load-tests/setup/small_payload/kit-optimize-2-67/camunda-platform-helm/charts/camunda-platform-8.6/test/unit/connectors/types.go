package connectors

type ConnectorsConfigYAML struct {
	Server  ServerYAML  `yaml:"server"`
	Camunda CamundaYAML `yaml:"camunda"`
	Zeebe   ZeebeYAML   `yaml:"zeebe"`
}

type IdentityYAML struct {
	Url      string `yaml:"url"`
	Audience string `yaml:"audience"`
	ClientId string `yaml:"client-id"`
}

type ServerYAML struct {
	Servlet ServletYAML `yaml:"servlet"`
}

type ServletYAML struct {
	ContextPath string `yaml:"context-path"`
}

type CamundaYAML struct {
	Connector ConnectorYAML `yaml:"connector"`
	Operate   OperateYAML   `yaml:"operate"`
	Identity  IdentityYAML  `yaml:"identity"`
}

type ConnectorYAML struct {
	Polling PollingYAML `yaml:"polling"`
	WebHook WebHookYAML `yaml:"webhook"`
}

type PollingYAML struct {
	Enabled string `yaml:"enabled"`
}

type WebHookYAML struct {
	Enabled string `yaml:"enabled"`
}

type OperateYAML struct {
	Client ClientYAML `yaml:"client"`
}

type ClientYAML struct {
	KeycloakTokenURL string `yaml:"keycloakTokenUrl"`
	ClientId         string `yaml:"clientId"`
	Url              string `yaml:"url"`
	Username         string `yaml:"username"`
}

type ZeebeYAML struct {
	Client ZeebeClientYAML `yaml:"client"`
}

type ZeebeClientYAML struct {
	Broker   BrokerYAML   `yaml:"broker"`
	Security SecurityYAML `yaml:"security"`
}

type BrokerYAML struct {
	GatewayAddress string `yaml:"gateway-address"`
}

type SecurityYAML struct {
	Plaintext string `yaml:"plaintext"`
}
