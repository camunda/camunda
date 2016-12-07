package org.camunda.tngp.example.msgpack.impl;

public class JsonPathQuery
{

    /*
     * Ein query ist
     *   * eine Sequenz von Operatoren
     *
     * Ein Operator
     *   * schränkt die aktuelle Auswahl ein (Filter);
     *     input: Einer Menge von Objekten (Array oder Map)
     *     output: Eine Menge von Objekten
     *   * geht eine Ebene tiefer (property-selector);
     *     input/output analog
     *   * konvertiert die Auswahl (recursive descent);
     *     input/output analog
     */


    // TODO: should be possible to serialize and deserialize this (=> so that the compiled query can be
    //   part of the parsed bpmn definition)
}
