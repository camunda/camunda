module.exports = {
    extends: ['@commitlint/config-conventional'],

    helpUrl: 'https://github.com/camunda/zeebe/blob/main/CONTRIBUTING.md/#commit-message-guidelines',

    // Ignore commits created by dependabot. We cannot guarantee that these don't violate our commit rules.
    ignores: [(msg) => /Signed-off-by: dependabot\[bot\]/m.test(msg)],

    // Rules are made up by a name and a configuration array. The configuration array contains:
    // - Level [0..2]: 0 disables the rule. For 1 it will be considered a warning for 2 an error.
    // - Applicable always|never: never inverts the rule.
    // - Value: value to use for this rule.
    rules: {
        // error rules
        'body-max-line-length': [2, 'always', 120],
        'header-max-length': [2, 'always', 120],
        'type-case': [2, 'always', 'lower-case'],
        'type-empty': [2, 'never'],
        'type-enum': [
            2,
            'always',
            [
                `build`, // Changes that affect the build system (e.g. Maven, Docker, etc)
                `ci`, // Changes to our CI configuration files and scripts (e.g. Jenkins, Bors, etc)
                `deps`, // A change to the external dependencies (was already used by Dependabot)
                `docs`, //  A change to the documentation
                `feat`, // A new feature (both internal or user-facing)
                `fix`, // A bug fix (both internal or user-facing)
                'merge', // Merges one or multiple commits
                `perf`, // A code change that improves performance
                `refactor`, // A code change that does not change the behavior
                'revert', // Reverts any change
                `style`, // A change to align the code with our style guide
                `test`, // Adding missing tests or correcting existing tests
            ],
        ],

        // warning rules


        // disabled rules
        'body-full-stop': [0, 'never', '.'],
        'body-leading-blank': [0, 'always'],
        'body-empty': [0, 'never'],
        'body-max-length': [0, 'always', Infinity],
        'body-min-length': [0, 'always', 0],
        'body-case': [0, 'always', 'lower-case'],
        'footer-leading-blank': [0, 'always'],
        'footer-empty': [0, 'never'],
        'footer-max-length': [0, 'always', Infinity],
        'footer-max-line-length': [0, 'always', 100],
        'footer-min-length': [0, 'always', 0],
        'header-case': [0, 'always', 'lower-case'],
        'header-full-stop': [0, 'never', '.'],
        'header-min-length': [0, 'always', 0],
        'references-empty': [0, 'never'],
        'scope-enum': [0, 'always', []],
        'scope-case': [0, 'always', 'lower-case'],
        'scope-empty': [0, 'never'],
        'scope-max-length': [0, 'always', Infinity],
        'scope-min-length': [0, 'always', 0],
        'subject-case': [0, 'always', 'lower-case'],
        'subject-empty': [0, 'never'],
        'subject-full-stop': [0, 'never', '.'],
        'subject-max-length': [0, 'always', Infinity],
        'subject-min-length': [0, 'always', 0],
        'subject-exclamation-mark': [0, 'never'],
        'type-max-length': [0, 'always', Infinity],
        'type-min-length': [0, 'always', 0],
        'signed-off-by': [0, 'always', 'Signed-off-by:'],
        'trailer-exists': [0, 'always', 'Signed-off-by:'],
    },
};
