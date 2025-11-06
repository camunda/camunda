package types

import (
	"context"
	"net/url"
	"os"
	"os/exec"
	"strings"
)

const DefaultElasticsearchURL = "http://localhost:9200"

type C8Run interface {
	OpenBrowser(ctx context.Context, url string) error
	ProcessTree(commandPid int) []*os.Process
	VersionCmd(ctx context.Context, javaBinaryPath string) *exec.Cmd
	ElasticsearchCmd(ctx context.Context, elasticsearchVersion string, parentDir string) *exec.Cmd
	ConnectorsCmd(ctx context.Context, javaBinary string, parentDir string, camundaVersion string, camundaPort int) *exec.Cmd
	CamundaCmd(ctx context.Context, camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd
}

type C8RunSettings struct {
	Config               string
	Detached             bool
	Port                 int
	Keystore             string
	KeystorePassword     string
	LogLevel             string
	SecondaryStorageType string
	SecondaryStorageURL  string
	Username             string
	Password             string
	Docker               bool
	StartupUrl           string
}

// HasKeyStore returns true when the keystore and password are set
func (c C8RunSettings) HasKeyStore() bool {
	return c.Keystore != "" && c.KeystorePassword != ""
}

// GetProtocol resolves the protocol to use for accessing Camunda endpoints
func (c C8RunSettings) GetProtocol() string {
	protocol := "http"
	if c.HasKeyStore() {
		protocol = "https"
	}
	return protocol
}

// UsesElasticsearch returns true when Elasticsearch should be started/stopped alongside Camunda.
// The default is Elasticsearch unless the configuration explicitly selects a different backend.
func (c C8RunSettings) UsesElasticsearch() bool {
	return strings.TrimSpace(c.SecondaryStorageType) == "" || strings.EqualFold(c.SecondaryStorageType, "elasticsearch")
}

// ElasticsearchURL resolves the Elasticsearch base URL, defaulting to localhost when none is provided.
func (c C8RunSettings) ElasticsearchURL() string {
	url := strings.TrimSpace(c.SecondaryStorageURL)
	if url == "" {
		return DefaultElasticsearchURL
	}
	return url
}

// ManagesElasticsearchProcess returns true when c8run should start/stop the bundled Elasticsearch.
// We only manage the process for local hosts; remote URLs imply an externally managed cluster.
func (c C8RunSettings) ManagesElasticsearchProcess() bool {
	if !c.UsesElasticsearch() {
		return false
	}
	return isLocalElasticsearchURL(c.ElasticsearchURL())
}

type Processes struct {
	Camunda       Process
	Connectors    Process
	Elasticsearch Process
}

type Process struct {
	Version string
	PidPath string
}

type State struct {
	C8          C8Run
	Settings    C8RunSettings
	ProcessInfo Processes
}

func isLocalElasticsearchURL(raw string) bool {
	parsed, err := url.Parse(strings.TrimSpace(raw))
	if err != nil {
		// Unknown URL format; assume local to preserve previous behaviour.
		return true
	}
	host := strings.ToLower(parsed.Hostname())
	if host == "" {
		return true
	}
	switch host {
	case "localhost", "127.0.0.1", "::1":
		return true
	}
	return false
}
