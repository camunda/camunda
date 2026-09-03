"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.genDts = void 0;
var flatten_1 = __importDefault(require("lodash/flatten"));
var isArray_1 = __importDefault(require("lodash/isArray"));
var map_1 = __importDefault(require("lodash/map"));
var reduce_1 = __importDefault(require("lodash/reduce"));
var uniq_1 = __importDefault(require("lodash/uniq"));
var upperFirst_1 = __importDefault(require("lodash/upperFirst"));
function genDts(model, options) {
    var contentParts = [];
    contentParts = contentParts.concat("import type { CstNode, ICstVisitor, IToken } from \"chevrotain\";");
    contentParts = contentParts.concat((0, flatten_1.default)((0, map_1.default)(model, function (node) { return genCstNodeTypes(node); })));
    if (options.includeVisitorInterface) {
        contentParts = contentParts.concat(genVisitor(options.visitorInterfaceName, model));
    }
    return contentParts.join("\n\n") + "\n";
}
exports.genDts = genDts;
function genCstNodeTypes(node) {
    var nodeCstInterface = genNodeInterface(node);
    var nodeChildrenInterface = genNodeChildrenType(node);
    return [nodeCstInterface, nodeChildrenInterface];
}
function genNodeInterface(node) {
    var nodeInterfaceName = getNodeInterfaceName(node.name);
    var childrenTypeName = getNodeChildrenTypeName(node.name);
    return "export interface ".concat(nodeInterfaceName, " extends CstNode {\n  name: \"").concat(node.name, "\";\n  children: ").concat(childrenTypeName, ";\n}");
}
function genNodeChildrenType(node) {
    var typeName = getNodeChildrenTypeName(node.name);
    return "export type ".concat(typeName, " = {\n  ").concat((0, map_1.default)(node.properties, function (property) { return genChildProperty(property); }).join("\n  "), "\n};");
}
function genChildProperty(prop) {
    var typeName = buildTypeString(prop.type);
    return "".concat(prop.name).concat(prop.optional ? "?" : "", ": ").concat(typeName, "[];");
}
function genVisitor(name, nodes) {
    return "export interface ".concat(name, "<IN, OUT> extends ICstVisitor<IN, OUT> {\n  ").concat((0, map_1.default)(nodes, function (node) { return genVisitorFunction(node); }).join("\n  "), "\n}");
}
function genVisitorFunction(node) {
    var childrenTypeName = getNodeChildrenTypeName(node.name);
    return "".concat(node.name, "(children: ").concat(childrenTypeName, ", param?: IN): OUT;");
}
function buildTypeString(type) {
    if ((0, isArray_1.default)(type)) {
        var typeNames = (0, uniq_1.default)((0, map_1.default)(type, function (t) { return getTypeString(t); }));
        var typeString = (0, reduce_1.default)(typeNames, function (sum, t) { return sum + " | " + t; });
        return "(" + typeString + ")";
    }
    else {
        return getTypeString(type);
    }
}
function getTypeString(type) {
    if (type.kind === "token") {
        return "IToken";
    }
    return getNodeInterfaceName(type.name);
}
function getNodeInterfaceName(ruleName) {
    return (0, upperFirst_1.default)(ruleName) + "CstNode";
}
function getNodeChildrenTypeName(ruleName) {
    return (0, upperFirst_1.default)(ruleName) + "CstChildren";
}
//# sourceMappingURL=generate.js.map