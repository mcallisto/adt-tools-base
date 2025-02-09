/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.XmlContext
import com.google.common.annotations.Beta
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * A wrapper for an XML parser. This allows tools integrating lint to map directly
 * to builtin services, such as already-parsed data structures in XML editors.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
abstract class XmlParser {
    /**
     * Parse the given XML content and returns as a Document
     *
     * @param file the file to be parsed
     * @return the parsed DOM document
     */
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    abstract fun parseXml(file: File): Document?

    /**
     * Parse the given XML string and return document, or null if any error
     * occurs (does **not** throw parsing exceptions). Most clients should
     * call [.parseXml] instead.
     *
     * @param xml  the parsing string
     * @param file the file corresponding to the XML string, **if known**.
     * May be null.
     * @return the parsed DOM document, or null if parsing fails
     */
    abstract fun parseXml(xml: CharSequence, file: File): Document?

    /**
     * Parse the file pointed to by the given context and return as a Document
     *
     * @param context the context pointing to the file to be parsed, typically
     * via [Context.getContents] but the file handle (
     * [Context.file] can also be used to map to an existing
     * editor buffer in the surrounding tool, etc)
     * @return the parsed DOM document, or null if parsing fails
     */
    abstract fun parseXml(context: XmlContext): Document?

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getLocation(context: XmlContext, node: Node): Location

    /**
     * Attempt to create a location for a given XML node. Note that since DOM does not normally
     * provide offset information for nodes, this doesn't work if you pass in a random DOM node
     * from your own parsing operations; you should only call this method with nodes provided
     * by lint in the first place (internally it uses a special parser which tracks offset
     * information.)
     *
     * @param file the file that contains the node that was parsed
     * @param node the node itself
     * @return a location for the node, if possible
     */
    abstract fun getLocation(file: File, node: Node): Location

    /**
     * Returns a [Location] for the given DOM node. Like
     * [.getLocation], but allows a position range that
     * is a subset of the node range.
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @param start the starting position within the node, inclusive
     * @param end the ending position within the node, exclusive
     * @return a location for the given node
     */
    abstract fun getLocation(context: XmlContext, node: Node,
            start: Int, end: Int): Location

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getNameLocation(context: XmlContext, node: Node): Location

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getValueLocation(context: XmlContext, node: Attr): Location

    /**
     * Creates a light-weight handle to a location for the given node. It can be
     * turned into a full fledged location by
     * [com.android.tools.lint.detector.api.Location.Handle.resolve].
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location handle
     * for
     * @return a location handle
     */
    abstract fun createLocationHandle(context: XmlContext,
            node: Node): Location.Handle

    /**
     * Dispose any data structures held for the given context.
     * @param context information about the file previously parsed
     * @param document the document that was parsed and is now being disposed
     */
    open fun dispose(context: XmlContext, document: Document) {}

    /**
     * Returns the start offset of the given node, or -1 if not known
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location handle
     * for
     * @return the start offset, or -1 if not known
     */
    abstract fun getNodeStartOffset(context: XmlContext, node: Node): Int

    /**
     * Returns the end offset of the given node, or -1 if not known
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location handle
     * for
     * @return the end offset, or -1 if not known
     */
    abstract fun getNodeEndOffset(context: XmlContext, node: Node): Int

    /**
     * Returns the leaf node at the given offset (biased towards the right), or null if not found
     *
     * @param offset the offset to search at
     * @return the leaf node, if any
     */
    abstract fun findNodeAt(context: XmlContext, offset: Int): Node?
}
