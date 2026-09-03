import { Octokit } from "@octokit/rest";
import { YAMLException } from "yaml-ast-parser";
export declare enum Kind {
    SCALAR = 0,
    MAPPING = 1,
    MAP = 2,
    SEQ = 3,
    ANCHOR_REF = 4,
    INCLUDE_REF = 5
}
export interface YAMLDocument {
    startPosition: number;
    endPosition: number;
    errors: YAMLException[];
}
export interface YAMLNode extends YAMLDocument {
    startPosition: number;
    endPosition: number;
    kind: Kind;
    anchorId?: string;
    valueObject?: any;
    parent: YAMLNode;
    errors: YAMLException[];
    value?: any;
    key?: any;
    mappings?: any;
}
export interface YAMLAnchorReference extends YAMLNode {
    kind: Kind.ANCHOR_REF;
    referencesAnchor: string;
    value: YAMLNode;
}
export interface YAMLScalar extends YAMLNode {
    kind: Kind.SCALAR;
    value: string;
    doubleQuoted?: boolean;
    singleQuoted?: boolean;
    plainScalar?: boolean;
    rawValue: string;
}
export interface YAMLMapping extends YAMLNode {
    kind: Kind.MAPPING;
    key: YAMLScalar;
    value: YAMLNode;
}
export interface YAMLSequence extends YAMLNode {
    kind: Kind.SEQ;
    items: YAMLNode[];
}
export interface YAMLMap extends YAMLNode {
    kind: Kind.MAP;
    mappings: YAMLMapping[];
}
export declare type YNode = YAMLMap | YAMLMapping | YAMLSequence | YAMLScalar;
export interface Context {
    /** Octokit client to use for dynamic auto completion */
    client: Octokit;
    /** Repository owner */
    owner: string;
    /** Repository name */
    repository: string;
    /** Is the repository owned by an organization? */
    ownerIsOrg?: boolean;
    /**
     * Are org features enabled, i.e., is the client authenticated for making org calls, which
     * means does it have the admin:org scope
     */
    orgFeaturesEnabled?: boolean;
    /**
     * Dynamic auto-completion/validations are cached for a certain time to speed up successive
     * operations.
     *
     * Setting this to a low number will greatly increase the number of API calls and duration
     * parsing/validation/auto-completion will take.
     *
     * @default 10 * 60 * 1000 = 10 minutes
     **/
    timeToCacheResponsesInMS?: number;
}
export declare type Position = [number, number];
export interface CompletionOption {
    /** Auto complete value */
    value: string;
    /** Optional description for this completion option */
    description?: string;
}
export declare enum DiagnosticKind {
    Error = 0,
    Warning = 1,
    Information = 2
}
export interface Diagnostic {
    /** Defaults to error */
    kind?: DiagnosticKind;
    message: string;
    pos: Position;
}
export interface Hover {
    /** Description for the hover, might be formatted with markdown */
    description: string;
}
