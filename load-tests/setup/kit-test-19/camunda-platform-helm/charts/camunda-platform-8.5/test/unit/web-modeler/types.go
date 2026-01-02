package web_modeler

// REST API ---
type WebModelerRestAPIApplicationYAML struct {
	Camunda CamundaYAML `yaml:"camunda"`
	Spring  SpringYAML  `yaml:"spring"`
}

type SpringYAML struct {
	Mail       MailYAML           `yaml:"mail"`
	Datasource DatasourceYAML     `yaml:"datasource"`
	Security   SpringSecurityYAML `yaml:"security"`
}
type DatasourceYAML struct {
	Url      string `yaml:"url"`
	Username string `yaml:"username"`
}

type MailYAML struct {
	Username string `yaml:"username"`
}

type SpringSecurityYAML struct {
	OAuth2 OAuth2YAML `yaml:"oauth2"`
}

type OAuth2YAML struct {
	ResourceServer ResourceServerYAML `yaml:"resourceserver"`
}

type ResourceServerYAML struct {
	JWT SpringJwtYAML `yaml:"jwt"`
}

type SpringJwtYAML struct {
	JwkSetURI string `yaml:"jwk-set-uri"`
}

type CamundaYAML struct {
	Modeler  ModelerYAML  `yaml:"modeler"`
	Identity IdentityYAML `yaml:"identity"`
}

type IdentityYAML struct {
	BaseURL string `yaml:"base-url"`
	Type    string `yaml:"type"`
}
type ModelerYAML struct {
	Security ModelerSecurityYAML `yaml:"security"`
}

type ModelerSecurityYAML struct {
	JWT ModelerJwtYAML `yaml:"jwt"`
}

type ModelerJwtYAML struct {
	Audience AudienceYAML `yaml:"audience"`
	Issuer   IssuerYAML   `yaml:"issuer"`
}

type IssuerYAML struct {
	BackendUrl string `yaml:"backend-url"`
}

type AudienceYAML struct {
	InternalAPI string `yaml:"internal-api"`
	PublicAPI   string `yaml:"public-api"`
}

// Web App ---

type WebModelerWebAppTOML struct {
	OAuth2   OAuth2Config   `toml:"oAuth2"`
	Client   ClientConfig   `toml:"client"`
	Identity IdentityConfig `toml:"identity"`
	Server   ServerConfig   `toml:"server"`
}
type ServerConfig struct {
	HttpsOnly string `toml:"httpsOnly"`
}
type IdentityConfig struct {
	BaseUrl string `toml:"baseUrl"`
}

type ClientConfig struct {
	Pusher PusherConfig `toml:"pusher"`
}

type PusherConfig struct {
	Host     string `toml:"host"`
	Port     string `toml:"port"`
	Path     string `toml:"path"`
	ForceTLS string `toml:"forceTLS"`
}

type OAuth2Config struct {
	Token    TokenConfig `toml:"token"`
	ClientId string      `toml:"clientId"`
	Type     string      `toml:"type"`
}

type TokenConfig struct {
	Audience string `toml:"audience"`
	JwksUrl  string `toml:"jwksUrl"`
}
