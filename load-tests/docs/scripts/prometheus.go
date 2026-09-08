package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type prometheusAPI interface {
	runtimeInfo() (prometheusResponse, error)
	query(string) (prometheusResponse, error)
}

type prometheusClient struct {
	endpoint   string
	headers    http.Header
	httpClient *http.Client
	timeAnchor string
}

type prometheusResponse struct {
	Status string         `json:"status"`
	Data   prometheusData `json:"data"`
}

type prometheusData struct {
	Result []prometheusSeries `json:"result"`
}

type prometheusSeries struct {
	Metric map[string]string `json:"metric"`
	Value  []json.RawMessage `json:"value"`
}

func newPrometheusClient(opts options) (prometheusClient, error) {
	headers, err := authHeaders(opts)
	if err != nil {
		return prometheusClient{}, err
	}
	return prometheusClient{
		endpoint:   strings.TrimRight(opts.endpoint, "/"),
		headers:    headers,
		httpClient: &http.Client{},
		timeAnchor: opts.timeAnchor,
	}, nil
}

func authHeaders(opts options) (http.Header, error) {
	if opts.bearerToken != "" && (opts.basicAuthUser != "" || opts.basicAuthPass != "") {
		return nil, errors.New("--token cannot be combined with --user/--password.")
	}
	if (opts.basicAuthUser == "") != (opts.basicAuthPass == "") {
		return nil, errors.New("--user and --password must be provided together.")
	}

	headers := http.Header{}
	if opts.bearerToken != "" {
		headers.Set("Authorization", "Bearer "+opts.bearerToken)
	}
	if opts.basicAuthUser != "" {
		credentials := opts.basicAuthUser + ":" + opts.basicAuthPass
		headers.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(credentials)))
	}
	return headers, nil
}

func (c prometheusClient) runtimeInfo() (prometheusResponse, error) {
	return c.getJSON(c.endpoint+"/api/v1/status/runtimeinfo", 15*time.Second)
}

func (c prometheusClient) query(query string) (prometheusResponse, error) {
	values := url.Values{"query": []string{query}}
	if c.timeAnchor != "" {
		values.Set("time", c.timeAnchor)
	}
	return c.getJSON(c.endpoint+"/api/v1/query?"+values.Encode(), 30*time.Second)
}

func (c prometheusClient) getJSON(rawURL string, timeout time.Duration) (prometheusResponse, error) {
	request, err := http.NewRequest(http.MethodGet, rawURL, nil)
	if err != nil {
		return prometheusResponse{}, err
	}
	request.Header = c.headers.Clone()

	httpClient := *c.httpClient
	httpClient.Timeout = timeout
	response, err := httpClient.Do(request)
	if err != nil {
		return prometheusResponse{}, err
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		_, _ = io.Copy(io.Discard, response.Body)
		return prometheusResponse{}, fmt.Errorf("Prometheus returned HTTP %d.", response.StatusCode)
	}

	var parsed prometheusResponse
	if err := json.NewDecoder(response.Body).Decode(&parsed); err != nil {
		return prometheusResponse{}, fmt.Errorf("Prometheus returned invalid JSON: %w", err)
	}
	return parsed, nil
}

func sampleString(value json.RawMessage) string {
	var decoded any
	decoder := json.NewDecoder(bytes.NewReader(value))
	decoder.UseNumber()
	if err := decoder.Decode(&decoded); err != nil {
		return ""
	}
	switch typedValue := decoded.(type) {
	case string:
		return typedValue
	case json.Number:
		return typedValue.String()
	default:
		return ""
	}
}

func checkEndpoint(client prometheusAPI, endpoint string) error {
	response, err := client.runtimeInfo()
	if err != nil {
		return errors.New(prometheusEndpointHelp(endpoint))
	}
	if response.Status != "success" {
		return fmt.Errorf("Endpoint '%s' is reachable, but it did not return a Prometheus API success response.\n\nIf you are running locally, make sure the port-forward points at Prometheus:\n\n  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090", endpoint)
	}
	return nil
}

func prometheusEndpointHelp(endpoint string) string {
	return fmt.Sprintf(`Could not reach Prometheus endpoint '%s'.

If you are running locally, start the port-forward in another terminal:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090

Then rerun this script with:

  --endpoint http://localhost:9090

If you are using the CI monitor ingress, verify the URL and pass credentials with
--user/--password, for example:

  --endpoint https://ci-monitor.benchmark.camunda.cloud --user "$PROM_USER" --password "$PROM_PASS"`, endpoint)
}
