package logging

import (
	"context"
	"io"
	"os"
	"strings"
	"time"

	"github.com/jwalton/gchalk"
	"github.com/rs/zerolog"
	"golang.org/x/term"
)

// Logger is the global logger configured by Setup.
var Logger zerolog.Logger

// ColorEnabled controls whether chalk styling is emitted.
var ColorEnabled = true

// Initialized indicates whether Setup has been called successfully.
var Initialized = false

type ctxKey struct{}

// Compact toggles compact prefix formatting for command output logs.
var Compact = false

type Options struct {
	UseJSON      bool
	LevelString  string
	ColorEnabled bool
}

func Setup(opts Options) error {
	level, err := parseLevel(opts.LevelString)
	if err != nil {
		level = zerolog.InfoLevel
	}
	ColorEnabled = opts.ColorEnabled && !opts.UseJSON
	// Respect standard color env vars and CI environments (e.g., GitHub Actions supports ANSI colors).
	if !opts.UseJSON {
		// Highest precedence: NO_COLOR disables color
		if _, ok := os.LookupEnv("NO_COLOR"); ok {
			ColorEnabled = false
		}
		// FORCE_COLOR enables color even when stdout is not a TTY
		if v, ok := os.LookupEnv("FORCE_COLOR"); ok && strings.TrimSpace(strings.ToLower(v)) != "0" && strings.TrimSpace(strings.ToLower(v)) != "false" {
			ColorEnabled = true
		}
		// Common CI envs that support ANSI colors (GitHub Actions, generic CI)
		if os.Getenv("GITHUB_ACTIONS") == "true" || os.Getenv("CI") == "true" {
			ColorEnabled = true
		}
	}

	var w io.Writer = os.Stdout
	if opts.UseJSON {
		zerolog.TimeFieldFormat = time.RFC3339
		Logger = zerolog.New(w).Level(level).With().Timestamp().Logger()
		Initialized = true
		return nil
	}

	cw := zerolog.ConsoleWriter{
		Out:        w,
		NoColor:    !ColorEnabled,
		TimeFormat: "15:04:05",
		FormatLevel: func(i interface{}) string {
			lvl := strings.ToUpper(toString(i))
			if !ColorEnabled {
				return lvl
			}
			switch lvl {
			case "TRACE":
				return gchalk.Cyan(lvl)
			case "DEBUG":
				return gchalk.Blue(lvl)
			case "INFO":
				return gchalk.Green(lvl)
			case "WARN":
				return gchalk.Yellow(lvl)
			case "ERROR":
				return gchalk.Red(lvl)
			default:
				return lvl
			}
		},
	}
	Logger = zerolog.New(cw).Level(level).With().Timestamp().Logger()
	Initialized = true
	return nil
}

func parseLevel(s string) (zerolog.Level, error) {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "trace":
		return zerolog.TraceLevel, nil
	case "debug":
		return zerolog.DebugLevel, nil
	case "info", "":
		return zerolog.InfoLevel, nil
	case "warn", "warning":
		return zerolog.WarnLevel, nil
	case "error":
		return zerolog.ErrorLevel, nil
	default:
		return zerolog.InfoLevel, nil
	}
}

// Styler is a function that styles a string (compatible with gchalk color funcs).
type Styler func(...string) string

// Emphasize wraps a string with a style when color is enabled.
func Emphasize(s string, style Styler) string {
	if !ColorEnabled {
		return s
	}
	if style == nil {
		return s
	}
	return style(s)
}

func toString(i interface{}) string {
	if i == nil {
		return ""
	}
	switch v := i.(type) {
	case string:
		return v
	default:
		return ""
	}
}

func IsTerminal(fd uintptr) bool {
	return term.IsTerminal(int(fd))
}

// SetCompact enables/disables compact log prefixes.
func SetCompact(enabled bool) {
	Compact = enabled
}

// ContextWithFields attaches structured fields to context for logging enrichment.
func ContextWithFields(ctx context.Context, fields map[string]string) context.Context {
	if len(fields) == 0 {
		return ctx
	}
	return context.WithValue(ctx, ctxKey{}, fields)
}

// FieldsFromContext extracts structured fields used to enrich log lines.
func FieldsFromContext(ctx context.Context) map[string]string {
	if ctx == nil {
		return nil
	}
	if v := ctx.Value(ctxKey{}); v != nil {
		if m, ok := v.(map[string]string); ok {
			return m
		}
	}
	return nil
}

// BuildPrefix constructs a compact, human-friendly prefix from fields, ordered by importance.
// Order: jobId | version/flow/scenario | release | namespace | cmd
func BuildPrefix(fields map[string]string, cmd string) string {
	if len(fields) == 0 && cmd == "" {
		return ""
	}
	segment := func(label, val string, style Styler) string {
		if strings.TrimSpace(val) == "" {
			return ""
		}
		txt := label + val
		if ColorEnabled {
			txt = style(txt)
		}
		return "[" + txt + "]"
	}
	var b strings.Builder
	if Compact {
		// Compact mode: single-letter labels, truncated values, shortened flow and cmd
		if v := fields["jobId"]; v != "" {
			b.WriteString(segment("j:", v, gchalk.Blue))
		}
		ver := fields["version"]
		flow := fields["flow"]
		if flow != "" {
			flow = strings.ToLower(string(flow[0]))
		}
		scn := fields["scenario"]
		group := strings.TrimSpace(strings.Join([]string{ver, flow, scn}, "/"))
		group = strings.Trim(group, "/")
		if group != "" {
			b.WriteString(segment("", group, gchalk.Green))
		}
		if v := fields["release"]; v != "" {
			b.WriteString(segment("r:", truncateMiddle(v, 28), gchalk.Yellow))
		}
		if v := fields["namespace"]; v != "" {
			b.WriteString(segment("n:", truncateMiddle(v, 28), gchalk.Cyan))
		}
		if cmd != "" {
			b.WriteString(segment("c:", compactCmd(cmd), gchalk.Magenta))
		}
	} else {
		if v := fields["jobId"]; v != "" {
			b.WriteString(segment("job:", v, gchalk.Blue))
		}
		ver := fields["version"]
		flow := fields["flow"]
		scn := fields["scenario"]
		if ver != "" || flow != "" || scn != "" {
			group := strings.TrimSpace(strings.Join([]string{ver, flow, scn}, " "))
			group = strings.ReplaceAll(group, "  ", " ")
			b.WriteString(segment("", group, gchalk.Green))
		}
		if v := fields["release"]; v != "" {
			b.WriteString(segment("rel:", v, gchalk.Yellow))
		}
		if v := fields["namespace"]; v != "" {
			b.WriteString(segment("ns:", v, gchalk.Cyan))
		}
		if cmd != "" {
			b.WriteString(segment("cmd:", cmd, gchalk.Magenta))
		}
	}
	if b.Len() == 0 {
		return ""
	}
	return b.String() + " "
}

// PrefixFromContext builds a prefix from context fields, optionally including cmd.
func PrefixFromContext(ctx context.Context, cmd string) string {
	return BuildPrefix(FieldsFromContext(ctx), cmd)
}

func truncateMiddle(s string, max int) string {
	if max <= 3 || len(s) <= max {
		return s
	}
	keep := (max - 3) / 2
	start := s[:keep]
	end := s[len(s)-keep:]
	if (max-3)%2 == 1 {
		// bias to start by one char
		start = s[:keep+1]
	}
	return start + "..." + end
}

func compactCmd(cmd string) string {
	switch strings.ToLower(cmd) {
	case "kubectl":
		return "k"
	case "helm":
		return "h"
	default:
		if len(cmd) > 1 {
			return strings.ToLower(cmd[:1])
		}
		return cmd
	}
}


