package cmd

import (
	"os"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(completionCmd)
}

var completionCmd = &cobra.Command{
	Use:   "completion [bash|zsh|fish|powershell]",
	Short: "Generate shell completion scripts",
	Long: `To load completions:

Bash:
  source <(camunda-deployer completion bash)

Zsh:
  camunda-deployer completion zsh > "${fpath[1]}/_camunda-deployer"

Fish:
  camunda-deployer completion fish | source

PowerShell:
  camunda-deployer completion powershell | Out-String | Invoke-Expression
`,
	Args: cobra.ExactValidArgs(1),
	ValidArgs: []string{
		"bash",
		"zsh",
		"fish",
		"powershell",
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		switch args[0] {
		case "bash":
			return rootCmd.GenBashCompletion(os.Stdout)
		case "zsh":
			return rootCmd.GenZshCompletion(os.Stdout)
		case "fish":
			return rootCmd.GenFishCompletion(os.Stdout, true)
		case "powershell":
			return rootCmd.GenPowerShellCompletionWithDesc(os.Stdout)
		default:
			return nil
		}
	},
}


