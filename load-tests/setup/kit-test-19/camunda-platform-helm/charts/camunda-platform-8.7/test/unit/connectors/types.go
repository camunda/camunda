package connectors

type ConnectorsConfigYAML struct {
	Server  ServerYAML  `yaml:"server"`
	Camunda CamundaYAML `yaml:"camunda"`
	Operate OperateYAML `yaml:"operate"`
}

type ServerYAML struct {
	Servlet ServletYAML `yaml:"servlet"`
}

type ServletYAML struct {
	ContextPath string `yaml:"context-path"`
}

type CamundaYAML struct {
	Connector struct {
		Polling PollingYAML `yaml:"polling"`
		WebHook WebHookYAML `yaml:"webhook"`
	} `yaml:"connector"`
	Identity struct {
		Url      string `yaml:"url"`
		Audience string `yaml:"audience"`
		ClientId string `yaml:"client-id"`
	} `yaml:"identity"`
	Client struct {
		Zeebe struct {
			RESTAddress string `yaml:"rest-address"`
			GRPCAddress string `yaml:"grpc-address"`
		} `yaml:"zeebe"`
	} `yaml:"client"`
}

type PollingYAML struct {
	Enabled string `yaml:"enabled"`
}

type WebHookYAML struct {
	Enabled string `yaml:"enabled"`
}

type OperateYAML struct {
	Client struct {
		KeycloakTokenURL string `yaml:"keycloakTokenUrl"`
		ClientId         string `yaml:"clientId"`
		BaseURL          string `yaml:"base-url"`
		Username         string `yaml:"username"`
	} `yaml:"client"`
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
