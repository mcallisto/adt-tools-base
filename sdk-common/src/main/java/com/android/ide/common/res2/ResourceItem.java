/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.common.res2;

import static com.android.SdkConstants.ANDROID_NEW_ID_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN;
import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INDEX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.ide.common.resources.ResourceResolver.ATTR_EXAMPLE;
import static com.android.ide.common.resources.ResourceResolver.XLIFF_G_TAG;
import static com.android.ide.common.resources.ResourceResolver.XLIFF_NAMESPACE_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.TextResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.nio.file.Paths;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A resource.
 *
 * <p>This includes the name, type, source file as a {@link ResourceFile} and an optional {@link
 * Node} in case of a resource coming from a value file.
 */
public class ResourceItem extends DataItem<ResourceFile>
        implements Configurable, Comparable<ResourceItem> {

    @NonNull private final ResourceType mType;

    /**
     * Namespace of the library this item came from.
     *
     * <p>This plays no part in the merging process, because the whole point of merging is that we
     * merge items from different namespaces. Unfortunately we use {@link ResourceItem} and {@link
     * ResourceValue} almost interchangeably, so for now this needs to be here.
     *
     * <p>TODO: Make {@link ResourceValue} {@link Configurable} and switch the whole repository
     * system to deal only with {@link ResourceValue} instances.
     */
    @Nullable private final String mNamespace;

    @Nullable private Node mValue;

    @Nullable private String mLibraryName;

    @Nullable protected ResourceValue mResourceValue;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * <p>Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param namespace the namespace of the resource
     * @param type the type of the resource
     * @param value an optional Node that represents the resource value.
     */
    public ResourceItem(
            @NonNull String name,
            @Nullable String namespace,
            @NonNull ResourceType type,
            @Nullable Node value,
            @Nullable String libraryName) {
        super(name);

        // The only exception is the empty "<public />" tag which means that all resources are
        // private.
        Preconditions.checkArgument(
                type == ResourceType.PUBLIC || !name.isEmpty(), "Resource name cannot be empty.");

        mNamespace = namespace;
        mType = type;
        mValue = value;
        mLibraryName = libraryName;
    }

    /**
     * Returns the type of the resource.
     *
     * @return the type.
     */
    @NonNull
    public ResourceType getType() {
        return mType;
    }

    /**
     * Returns the optional value of the resource. Can be null
     *
     * @return the value or null.
     */
    @Nullable
    public Node getValue() {
        return mValue;
    }

    /**
     * Returns the optional string value of the resource. Can be null
     *
     * @return the value or null.
     */
    @Nullable
    public String getValueText() {
        return mValue != null ? mValue.getTextContent() : null;
    }

    /**
     * Returns the library that the resource was found. This will be null for application resources.
     *
     * @return the library name or null.
     */
    @Nullable
    public String getLibraryName() {
        return mLibraryName;
    }

    /**
     * Returns the namespace where the resource was defined. This will be null for application
     * resources.
     *
     * @return the library name or null.
     */
    @Nullable
    public String getNamespace() {
        return mNamespace;
    }

    /**
     * Returns the resource item qualifiers.
     *
     * @return the qualifiers
     */
    @NonNull
    public String getQualifiers() {
        ResourceFile resourceFile = getSource();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getQualifier on " + toString());
        }

        return resourceFile.getQualifiers();
    }

    @NonNull
    public DataFile.FileType getSourceType() {
        ResourceFile resourceFile = getSource();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getSourceType on " + toString());
        }

        return resourceFile.getType();
    }

    /**
     * Builds a {@link ResourceUrl} that points to this {@link ResourceItem}.
     *
     * <p>For now we pass the "framework" flag like before, but soon we'll start relying only on the
     * namespace. Framework resources are not handled by the res2 system, so this shouldn't matter,
     * but let's take it one step at a time.
     */
    @NonNull
    public ResourceUrl getResourceUrl(boolean forceFramework) {
        if (forceFramework) {
            return ResourceUrl.create(mType, getName(), true);
        } else {
            return ResourceUrl.create(mNamespace, mType, getName());
        }
    }

    /**
     * Sets the value of the resource and set its state to TOUCHED.
     *
     * @param from the resource to copy the value from.
     */
    void setValue(@NonNull ResourceItem from) {
        mValue = from.mValue;
        setTouched();
    }

    @Override
    public FolderConfiguration getConfiguration() {
        ResourceFile resourceFile = getSource();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getConfiguration on " + toString());
        }
        return resourceFile.getFolderConfiguration();
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, and name.
     *
     * <p>If the resource has not been added to a {@link ResourceFile}, this will throw an {@link
     * IllegalStateException}.
     *
     * @return the key for this resource.
     * @throws IllegalStateException if the resource is not added to a ResourceFile
     */
    @Override
    public String getKey() {
        if (getSource() == null) {
            throw new IllegalStateException(
                    "ResourceItem.getKey called on object with no ResourceFile: " + this);
        }
        String qualifiers = getQualifiers();

        String typeName = mType.getName();
        if (mType == ResourceType.PUBLIC && mValue != null) {
            String typeAttribute = ((Element) mValue).getAttribute(ATTR_TYPE);
            if (typeAttribute != null) {
                typeName += "_" + typeAttribute;
            }
        }

        if (!qualifiers.isEmpty()) {
            return typeName + "-" + qualifiers + "/" + getName();
        }

        return typeName + "/" + getName();
    }

    @Override
    protected void wasTouched() {
        mResourceValue = null;
    }

    @Nullable
    public ResourceValue getResourceValue(boolean isFrameworks) {
        if (mResourceValue == null) {
            //noinspection VariableNotUsedInsideIf
            if (mValue == null) {
                // Density based resource value?
                Density density =
                        mType == ResourceType.DRAWABLE || mType == ResourceType.MIPMAP
                                ? getFolderDensity()
                                : null;

                ResourceFile source = getSource();
                assert source != null;

                if (density != null) {
                    mResourceValue =
                            new DensityBasedResourceValue(
                                    getResourceUrl(isFrameworks),
                                    source.getFile().getAbsolutePath(),
                                    density,
                                    mLibraryName);
                } else {
                    mResourceValue =
                            new ResourceValue(
                                    getResourceUrl(isFrameworks),
                                    source.getFile().getAbsolutePath(),
                                    mLibraryName);
                }
            } else {
                mResourceValue = parseXmlToResourceValue(isFrameworks);
            }
        }

        return mResourceValue;
    }

    // TODO: We should be storing shared FolderConfiguration instances on the ResourceFiles
    // instead. This is a temporary fix to make rendering work properly again.
    @Nullable
    private Density getFolderDensity() {
        String qualifiers = getQualifiers();
        if (!qualifiers.isEmpty() && qualifiers.contains("dpi")) {
            Iterable<String> segments = Splitter.on('-').split(qualifiers);
            FolderConfiguration config = FolderConfiguration.getConfigFromQualifiers(segments);
            if (config != null) {
                DensityQualifier densityQualifier = config.getDensityQualifier();
                if (densityQualifier != null) {
                    return densityQualifier.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns a formatted string usable in an XML to use for the {@link ResourceItem}.
     *
     * @param system Whether this is a system resource or a project resource.
     * @return a string in the format @[type]/[name]
     */
    public String getXmlString(ResourceType type, boolean system) {
        if (type == ResourceType.ID /* && isDeclaredInline()*/) {
            return (system ? ANDROID_NEW_ID_PREFIX : NEW_ID_PREFIX) + "/" + getName();
        }

        return (system ? ANDROID_PREFIX : PREFIX_RESOURCE_REF) + type.getName() + "/" + getName();
    }

    /**
     * Compares the ResourceItem {@link #getValue()} together and returns true if they are the same.
     *
     * @param resource The ResourceItem object to compare to.
     * @return true if equal
     */
    public boolean compareValueWith(ResourceItem resource) {
        if (mValue != null && resource.mValue != null) {
            return NodeUtils.compareElementNode(mValue, resource.mValue, true);
        }

        return mValue == resource.mValue;
    }

    @Override
    public String toString() {
        return "ResourceItem{"
                + "mName='"
                + getName()
                + '\''
                + ", mType="
                + mType
                + ", mStatus="
                + getStatus()
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ResourceItem that = (ResourceItem) o;

        return mType == that.mType;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mType.hashCode();
        return result;
    }

    @Nullable
    private ResourceValue parseXmlToResourceValue(boolean isFrameworks) {
        assert mValue != null;

        final NamedNodeMap attributes = mValue.getAttributes();

        ResourceValue value;
        String name = getName();

        switch (mType) {
            case STYLE:
                String parent = getAttributeValue(attributes, ATTR_PARENT);
                try {
                    value =
                            parseStyleValue(
                                    new StyleResourceValue(
                                            getResourceUrl(isFrameworks), parent, mLibraryName));
                } catch (Exception ignored) {
                    return null;
                }
                break;
            case DECLARE_STYLEABLE:
                value =
                        parseDeclareStyleable(
                                new DeclareStyleableResourceValue(
                                        getResourceUrl(isFrameworks), null, mLibraryName));
                break;
            case ARRAY:
                ArrayResourceValue arrayValue =
                        new ArrayResourceValue(getResourceUrl(isFrameworks), mLibraryName) {
                            @Override
                            protected int getDefaultIndex() {
                                // Allow the user to specify a specific element to use via tools:index
                                String toolsDefaultIndex =
                                        getAttributeValueNS(attributes, TOOLS_URI, ATTR_INDEX);
                                if (toolsDefaultIndex != null) {
                                    try {
                                        return Integer.parseInt(toolsDefaultIndex);
                                    } catch (NumberFormatException e) {
                                        return super.getDefaultIndex();
                                    }
                                }
                                return super.getDefaultIndex();
                            }
                        };
                value = parseArrayValue(arrayValue);
                break;
            case PLURALS:
                PluralsResourceValue pluralsResourceValue =
                        new PluralsResourceValue(getResourceUrl(isFrameworks), null, mLibraryName) {
                            @Override
                            public String getValue() {
                                // Allow the user to specify tools:quantity.
                                String quantity =
                                        getAttributeValueNS(attributes, TOOLS_URI, ATTR_QUANTITY);
                                if (quantity != null) {
                                    String value = getValue(quantity);
                                    if (value != null) {
                                        return value;
                                    }
                                }
                                return super.getValue();
                            }
                        };
                value = parsePluralsValue(pluralsResourceValue);
                break;
            case ATTR:
                value =
                        parseAttrValue(
                                new AttrResourceValue(getResourceUrl(isFrameworks), mLibraryName));
                break;
            case STRING:
                value =
                        parseTextValue(
                                new TextResourceValue(
                                        getResourceUrl(isFrameworks), null, null, mLibraryName));
                break;
            case ANIMATOR:
            case DRAWABLE:
            case INTERPOLATOR:
            case LAYOUT:
            case MENU:
            case MIPMAP:
            case TRANSITION:
                value =
                        parseFileName(
                                new ResourceValue(getResourceUrl(isFrameworks), mLibraryName));
                break;
            default:
                value =
                        parseValue(
                                new ResourceValue(
                                        getResourceUrl(isFrameworks), null, mLibraryName));
                break;
        }

        return value;
    }

    @Nullable
    private static String getAttributeValue(NamedNodeMap attributes, String attributeName) {
        Attr attribute = (Attr) attributes.getNamedItem(attributeName);
        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    @Nullable
    private static String getAttributeValueNS(
            NamedNodeMap attributes, String namespaceURI, String attributeName) {
        Attr attribute = (Attr) attributes.getNamedItemNS(namespaceURI, attributeName);
        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    @NonNull
    private ResourceValue parseStyleValue(@NonNull StyleResourceValue styleValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {

                    // is the attribute in the android namespace?
                    boolean isFrameworkAttr = styleValue.isFramework();
                    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
                        isFrameworkAttr = true;
                    }

                    String value =
                            ValueXmlHelper.unescapeResourceString(
                                    getTextNode(child.getChildNodes()), false, true);
                    ItemResourceValue resValue =
                            new ItemResourceValue(
                                    name,
                                    isFrameworkAttr,
                                    value,
                                    styleValue.isFramework(),
                                    styleValue.getLibraryName());
                    styleValue.addItem(resValue);
                }
            }
        }

        return styleValue;
    }

    @NonNull
    private AttrResourceValue parseAttrValue(@NonNull AttrResourceValue attrValue) {
        return parseAttrValue(mValue, attrValue);
    }

    @NonNull
    private static AttrResourceValue parseAttrValue(
            @NonNull Node valueNode, @NonNull AttrResourceValue attrValue) {
        NodeList children = valueNode.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {
                    String value = getAttributeValue(attributes, ATTR_VALUE);
                    if (value != null) {
                        try {
                            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                            // use Long.decode instead.
                            attrValue.addValue(name, (int) (long) Long.decode(value));
                        } catch (NumberFormatException e) {
                            // pass, we'll just ignore this value
                        }
                    }
                }
            }
        }

        return attrValue;
    }

    private ResourceValue parseArrayValue(ArrayResourceValue arrayValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String text = getTextNode(child.getChildNodes());
                text = ValueXmlHelper.unescapeResourceString(text, false, true);
                arrayValue.addElement(text);
            }
        }

        return arrayValue;
    }

    private ResourceValue parsePluralsValue(PluralsResourceValue value) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String quantity = getAttributeValue(attributes, ATTR_QUANTITY);
                if (quantity != null) {
                    String text = getTextNode(child.getChildNodes());
                    text = ValueXmlHelper.unescapeResourceString(text, false, true);
                    value.addPlural(quantity, text);
                }
            }
        }

        return value;
    }

    @NonNull
    private ResourceValue parseDeclareStyleable(
            @NonNull DeclareStyleableResourceValue declareStyleable) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {
                    // is the attribute in the android namespace?
                    boolean isFrameworkAttr = declareStyleable.isFramework();
                    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
                        isFrameworkAttr = true;
                    }

                    AttrResourceValue attr =
                            parseAttrValue(
                                    child,
                                    new AttrResourceValue(
                                            ResourceUrl.create(
                                                    ResourceType.ATTR, name, isFrameworkAttr),
                                            mLibraryName));
                    declareStyleable.addValue(attr);
                }
            }
        }

        return declareStyleable;
    }

    @NonNull
    private ResourceValue parseValue(@NonNull ResourceValue value) {
        String text = getTextNode(mValue.getChildNodes());
        value.setValue(ValueXmlHelper.unescapeResourceString(text, false, true));

        return value;
    }

    @NonNull
    private ResourceValue parseFileName(@NonNull ResourceValue value) {
        String text = getTextNode(mValue.getChildNodes()).trim();
        if (!text.isEmpty()
                && !text.startsWith(PREFIX_RESOURCE_REF)
                && !text.startsWith(PREFIX_THEME_REF)) {
            text = Paths.get(text).toString();
        }

        value.setValue(text);
        return value;
    }

    @NonNull
    private static String getTextNode(@NonNull NodeList children) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.ELEMENT_NODE:
                    {
                        Element element = (Element) child;
                        if (XLIFF_G_TAG.equals(element.getLocalName())
                                && element.getNamespaceURI() != null
                                && element.getNamespaceURI().startsWith(XLIFF_NAMESPACE_PREFIX)) {
                            if (element.hasAttribute(ATTR_EXAMPLE)) {
                                // <xliff:g id="number" example="7">%d</xliff:g> minutes
                                // => "(7) minutes"
                                String example = element.getAttribute(ATTR_EXAMPLE);
                                sb.append('(').append(example).append(')');
                                continue;
                            } else if (element.hasAttribute(ATTR_ID)) {
                                // Step <xliff:g id="step_number">%1$d</xliff:g>
                                // => Step ${step_number}
                                String id = element.getAttribute(ATTR_ID);
                                sb.append('$').append('{').append(id).append('}');
                                continue;
                            }
                        }

                        NodeList childNodes = child.getChildNodes();
                        if (childNodes.getLength() > 0) {
                            sb.append(getTextNode(childNodes));
                        }
                        break;
                    }
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue());
                    break;
                case Node.CDATA_SECTION_NODE:
                    sb.append(child.getNodeValue());
                    break;
            }
        }

        return sb.toString();
    }

    @NonNull
    private TextResourceValue parseTextValue(@NonNull TextResourceValue value) {
        NodeList children = mValue.getChildNodes();
        String text = getTextNode(children);
        value.setValue(ValueXmlHelper.unescapeResourceString(text, false, true));

        int length = children.getLength();

        if (length >= 1) {
            boolean haveElementChildrenOrCdata = false;
            for (int i = 0; i < length; i++) {
                if (children.item(i).getNodeType() == Node.ELEMENT_NODE
                        || children.item(i).getNodeType() == Node.CDATA_SECTION_NODE) {
                    haveElementChildrenOrCdata = true;
                    break;
                }
            }

            if (haveElementChildrenOrCdata) {
                String markupText = getMarkupText(children);
                value.setRawXmlValue(markupText);
            }
        }

        return value;
    }

    @NonNull
    private static String getMarkupText(@NonNull NodeList children) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.ELEMENT_NODE:
                    {
                        Element element = (Element) child;
                        String tagName = element.getTagName();
                        sb.append('<');
                        sb.append(tagName);

                        NamedNodeMap attributes = element.getAttributes();
                        int attributeCount = attributes.getLength();
                        if (attributeCount > 0) {
                            for (int j = 0; j < attributeCount; j++) {
                                Node attribute = attributes.item(j);
                                sb.append(' ');
                                sb.append(attribute.getNodeName());
                                sb.append('=').append('"');
                                XmlUtils.appendXmlAttributeValue(sb, attribute.getNodeValue());
                                sb.append('"');
                            }
                        }
                        sb.append('>');

                        NodeList childNodes = child.getChildNodes();
                        if (childNodes.getLength() > 0) {
                            sb.append(getMarkupText(childNodes));
                        }

                        sb.append('<');
                        sb.append('/');
                        sb.append(tagName);
                        sb.append('>');

                        break;
                    }
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue());
                    break;
                case Node.CDATA_SECTION_NODE:
                    sb.append("<![CDATA[");
                    sb.append(child.getNodeValue());
                    sb.append("]]>");
                    break;
            }
        }

        return sb.toString();
    }

    @Override
    public int compareTo(@NonNull ResourceItem resourceItem) {
        int comp = mType.compareTo(resourceItem.getType());
        if (comp == 0) {
            comp = getName().compareTo(resourceItem.getName());
        }

        return comp;
    }

    private boolean mIgnoredFromDiskMerge = false;

    public void setIgnoredFromDiskMerge(boolean ignored) {
        mIgnoredFromDiskMerge = ignored;
    }

    public boolean getIgnoredFromDiskMerge() {
        return mIgnoredFromDiskMerge;
    }

    // Used for the blob writing.
    // TODO: move this to ResourceMerger/Set.

    @Override
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        NodeUtils.addAttribute(document, node, null, ATTR_TYPE, mType.getName());
    }

    @Override
    Node getDetailsXml(Document document) {
        return NodeUtils.duplicateAndAdoptNode(document, mValue);
    }
}
