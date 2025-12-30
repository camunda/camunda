package executil

import (
	"bufio"
	"context"
	"os"
	"os/exec"
	"strings"
	"sync"

	"scripts/camunda-core/pkg/logging"
)

type bufferCallback func(level string, line string)

type bufferCtxKey struct{}

// ContextWithBuffer attaches a buffer callback to context for capturing command output
func ContextWithBuffer(ctx context.Context, callback bufferCallback) context.Context {
	return context.WithValue(ctx, bufferCtxKey{}, callback)
}

// getBufferFromContext extracts the buffer callback from context if present
func getBufferFromContext(ctx context.Context) bufferCallback {
	if v := ctx.Value(bufferCtxKey{}); v != nil {
		if cb, ok := v.(bufferCallback); ok {
			return cb
		}
	}
	return nil
}

func RunCommand(ctx context.Context, name string, args []string, env []string, workingDir string) error {
	cmd := exec.CommandContext(ctx, name, args...)
	if workingDir != "" {
		cmd.Dir = workingDir
	}
	if len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return err
	}

	if err := cmd.Start(); err != nil {
		return err
	}

	bufferCB := getBufferFromContext(ctx)
	if bufferCB != nil {
		// Buffered mode: capture output and send to callback
		var wg sync.WaitGroup
		wg.Add(2)
		go func() {
			defer wg.Done()
			sc := bufio.NewScanner(stdout)
			for sc.Scan() {
				bufferCB("info", sc.Text())
			}
		}()
		go func() {
			defer wg.Done()
			sc := bufio.NewScanner(stderr)
			for sc.Scan() {
				bufferCB("warn", sc.Text())
			}
		}()
		wg.Wait()
		return cmd.Wait()
	}

	// Normal mode: log directly
	baseLogger := logging.Logger
	if fields := logging.FieldsFromContext(ctx); len(fields) > 0 {
		b := baseLogger.With()
		for k, v := range fields {
			b = b.Str(k, v)
		}
		baseLogger = b.Logger()
	}

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stdout)
		for sc.Scan() {
			line := sc.Text()
			prefix := logging.PrefixFromContext(ctx, name)
			baseLogger.Info().Msg(prefix + line)
		}
	}()
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stderr)
		for sc.Scan() {
			line := sc.Text()
			prefix := logging.PrefixFromContext(ctx, name)
			baseLogger.Warn().Msg(prefix + line)
		}
	}()

	wg.Wait()
	return cmd.Wait()
}

func RunCommandCapture(ctx context.Context, name string, args []string, env []string, workingDir string) ([]byte, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	if workingDir != "" {
		cmd.Dir = workingDir
	}
	if len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}
	out, err := cmd.Output()
	if err != nil {
		return nil, err
	}
	return out, nil
}

func RunCommandWithStdin(ctx context.Context, name string, args []string, env []string, workingDir string, stdin []byte) error {
	cmd := exec.CommandContext(ctx, name, args...)
	if workingDir != "" {
		cmd.Dir = workingDir
	}
	if len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}
	stdinPipe, err := cmd.StdinPipe()
	if err != nil {
		return err
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return err
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	// write stdin
	go func() {
		defer stdinPipe.Close()
		_, _ = stdinPipe.Write(stdin)
	}()
	
	bufferCB := getBufferFromContext(ctx)
	if bufferCB != nil {
		// Buffered mode: capture output and send to callback
		var wg sync.WaitGroup
		wg.Add(2)
		go func() {
			defer wg.Done()
			sc := bufio.NewScanner(stdout)
			for sc.Scan() {
				bufferCB("info", sc.Text())
			}
		}()
		go func() {
			defer wg.Done()
			sc := bufio.NewScanner(stderr)
			for sc.Scan() {
				bufferCB("warn", sc.Text())
			}
		}()
		wg.Wait()
		return cmd.Wait()
	}
	
	// Normal mode: stream output
	baseLogger := logging.Logger
	if fields := logging.FieldsFromContext(ctx); len(fields) > 0 {
		b := baseLogger.With()
		for k, v := range fields {
			b = b.Str(k, v)
		}
		baseLogger = b.Logger()
	}
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stdout)
		for sc.Scan() {
			prefix := logging.PrefixFromContext(ctx, name)
			baseLogger.Info().Msg(prefix + sc.Text())
		}
	}()
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stderr)
		for sc.Scan() {
			prefix := logging.PrefixFromContext(ctx, name)
			baseLogger.Warn().Msg(prefix + sc.Text())
		}
	}()
	wg.Wait()
	return cmd.Wait()
}

// BufferedOutput holds captured stdout and stderr lines
type BufferedOutput struct {
	Stdout []string
	Stderr []string
}

// RunCommandBuffered executes a command and captures its output instead of logging directly.
// Returns the captured stdout/stderr lines separately.
func RunCommandBuffered(ctx context.Context, name string, args []string, env []string, workingDir string) (*BufferedOutput, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	if workingDir != "" {
		cmd.Dir = workingDir
	}
	if len(env) > 0 {
		cmd.Env = append(os.Environ(), env...)
	}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return nil, err
	}

	if err := cmd.Start(); err != nil {
		return nil, err
	}

	var stdoutLines []string
	var stderrLines []string
	var wg sync.WaitGroup
	wg.Add(2)
	
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stdout)
		for sc.Scan() {
			stdoutLines = append(stdoutLines, sc.Text())
		}
	}()
	
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stderr)
		for sc.Scan() {
			stderrLines = append(stderrLines, sc.Text())
		}
	}()

	wg.Wait()
	err = cmd.Wait()
	
	return &BufferedOutput{
		Stdout: stdoutLines,
		Stderr: stderrLines,
	}, err
}

// FieldsNoEmpty splits on whitespace and removes empty entries.
func FieldsNoEmpty(s string) []string {
	ff := strings.Fields(s)
	if len(ff) == 0 {
		return nil
	}
	return ff
}


