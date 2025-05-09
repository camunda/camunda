package shutdown

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// MockC8Run is a mock implementation of the C8Run interface
type MockC8Run struct {
	mock.Mock
}

func (m *MockC8Run) OpenBrowser(protocol string, port int) error {
	args := m.Called(protocol, port)
	return args.Error(0)
}

func (m *MockC8Run) ProcessTree(commandPid int) []types.C8RunProcess {
	args := m.Called(commandPid)
	return args.Get(0).([]types.C8RunProcess)
}

func (m *MockC8Run) VersionCmd(javaBinaryPath string) *exec.Cmd {
	args := m.Called(javaBinaryPath)
	return args.Get(0).(*exec.Cmd)
}

func (m *MockC8Run) ElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
	args := m.Called(elasticsearchVersion, parentDir)
	return args.Get(0).(*exec.Cmd)
}

func (m *MockC8Run) ConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
	args := m.Called(javaBinary, parentDir, camundaVersion)
	return args.Get(0).(*exec.Cmd)
}

func (m *MockC8Run) CamundaCmd(camundaVersion string, parentDir string, extraArgs string, javaOpts string) *exec.Cmd {
	args := m.Called(camundaVersion, parentDir, extraArgs, javaOpts)
	return args.Get(0).(*exec.Cmd)
}

type MockProcess struct {
	mock.Mock
}

func (m *MockProcess) Kill() error {
	args := m.Called()
	return args.Error(0)
}

func (m *MockProcess) Pid() int {
	args := m.Called()
	return args.Int(0)
}

func NewMockProcess(pid int) *MockProcess {
	mock := new(MockProcess)
	mock.On("Pid").Return(pid)
	mock.On("Kill").Return(nil)

	return mock
}

func createPidFiles(t *testing.T, tempDir string) types.Processes {
	camundaPidFile := filepath.Join(tempDir, "camunda.pid")
	connectorsPidFile := filepath.Join(tempDir, "connectors.pid")
	elasticsearchPidFile := filepath.Join(tempDir, "elasticsearch.pid")

	var err error
	err = os.WriteFile(camundaPidFile, []byte("1001"), 0644)
	assert.NoError(t, err)
	err = os.WriteFile(connectorsPidFile, []byte("1002"), 0644)
	assert.NoError(t, err)
	err = os.WriteFile(elasticsearchPidFile, []byte("1003"), 0644)
	assert.NoError(t, err)

	processes := types.Processes{
		Camunda: types.Process{
			Pid: camundaPidFile,
		},
		Connectors: types.Process{
			Pid: connectorsPidFile,
		},
		Elasticsearch: types.Process{
			Pid: elasticsearchPidFile,
		},
	}

	return processes
}

func TestShutdownProcesses(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "shutdown_test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	t.Run("Stop all services", func(t *testing.T) {
		mockCamundaProc := NewMockProcess(1001)
		mockConnectorsProc := NewMockProcess(1002)
		mockElasticsearchProc := NewMockProcess(1003)

		mockC8 := new(MockC8Run)
		mockC8.On("ProcessTree", 1001).Return([]types.C8RunProcess{mockCamundaProc})
		mockC8.On("ProcessTree", 1002).Return([]types.C8RunProcess{mockConnectorsProc})
		mockC8.On("ProcessTree", 1003).Return([]types.C8RunProcess{mockElasticsearchProc})

		settings := types.C8RunSettings{
			DisableElasticsearch: false,
		}

		ShutdownProcesses(mockC8, settings, createPidFiles(t, tempDir))

		mockCamundaProc.AssertExpectations(t)
		mockConnectorsProc.AssertExpectations(t)
		mockElasticsearchProc.AssertExpectations(t)
		mockC8.AssertExpectations(t)
	})

	t.Run("Stop with Elasticsearch disabled", func(t *testing.T) {
		mockCamundaProc := NewMockProcess(1001)
		mockConnectorsProc := NewMockProcess(1002)
		mockElasticsearchProc := NewMockProcess(1003)

		mockC8 := new(MockC8Run)
		mockC8.On("ProcessTree", 1001).Return([]types.C8RunProcess{mockCamundaProc})
		mockC8.On("ProcessTree", 1002).Return([]types.C8RunProcess{mockConnectorsProc})

		settings := types.C8RunSettings{
			DisableElasticsearch: true,
		}

		ShutdownProcesses(mockC8, settings, createPidFiles(t, tempDir))

		mockCamundaProc.AssertExpectations(t)
		mockConnectorsProc.AssertExpectations(t)
		mockElasticsearchProc.AssertNotCalled(t, "Kill")
		mockC8.AssertExpectations(t)
	})
}
