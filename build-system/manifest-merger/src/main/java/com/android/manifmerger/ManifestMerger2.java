/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import static com.android.manifmerger.PlaceholderHandler.APPLICATION_ID;
import static com.android.manifmerger.PlaceholderHandler.KeyBasedValueResolver;
import static com.android.manifmerger.PlaceholderHandler.PACKAGE_NAME;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * merges android manifest files, idempotent.
 */
@Immutable
public class ManifestMerger2 {

    static final String BOOTSTRAP_INSTANT_RUN_CONTENT_PROVIDER =
            "com.android.tools.ir.server.InstantRunContentProvider";

    @NonNull
    private final File mManifestFile;

    @NonNull
    private final Map<String, Object> mPlaceHolderValues;

    @NonNull
    private final KeyBasedValueResolver<ManifestSystemProperty> mSystemPropertyResolver;

    @NonNull
    private final ILogger mLogger;
    @NonNull
    private final ImmutableList<Pair<String, File>> mLibraryFiles;
    @NonNull
    private final ImmutableList<File> mFlavorsAndBuildTypeFiles;
    @NonNull
    private final ImmutableList<Invoker.Feature> mOptionalFeatures;
    @NonNull
    private final MergeType mMergeType;
    @NonNull
    private final XmlDocument.Type mDocumentType;
    @NonNull
    private final Optional<File> mReportFile;
    @NonNull private final String mFeatureName;
    @NonNull private final FileStreamProvider mFileStreamProvider;
    @NonNull private final ImmutableList<File> mNavigationFiles;

    private ManifestMerger2(
            @NonNull ILogger logger,
            @NonNull File mainManifestFile,
            @NonNull ImmutableList<Pair<String, File>> libraryFiles,
            @NonNull ImmutableList<File> flavorsAndBuildTypeFiles,
            @NonNull ImmutableList<Invoker.Feature> optionalFeatures,
            @NonNull Map<String, Object> placeHolderValues,
            @NonNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertiesResolver,
            @NonNull MergeType mergeType,
            @NonNull XmlDocument.Type documentType,
            @NonNull Optional<File> reportFile,
            @NonNull String featureName,
            @NonNull FileStreamProvider fileStreamProvider,
            @NonNull ImmutableList<File> navigationFiles) {
        this.mSystemPropertyResolver = systemPropertiesResolver;
        this.mPlaceHolderValues = placeHolderValues;
        this.mManifestFile = mainManifestFile;
        this.mLogger = logger;
        this.mLibraryFiles = libraryFiles;
        this.mFlavorsAndBuildTypeFiles = flavorsAndBuildTypeFiles;
        this.mOptionalFeatures = optionalFeatures;
        this.mMergeType = mergeType;
        this.mDocumentType = documentType;
        this.mReportFile = reportFile;
        this.mFeatureName = featureName;
        this.mFileStreamProvider = fileStreamProvider;
        this.mNavigationFiles = navigationFiles;
    }

    /**
     * Perform high level ordering of files merging and delegates actual merging to
     * {@link XmlDocument#merge(XmlDocument, com.android.manifmerger.MergingReport.Builder)}
     *
     * @return the merging activity report.
     * @throws MergeFailureException if the merging cannot be completed (for instance, if xml
     * files cannot be loaded).
     */
    @NonNull
    private MergingReport merge() throws MergeFailureException {
        // initiate a new merging report
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger, this);

        SelectorResolver selectors = new SelectorResolver();

        // load the main manifest file to do some checking along the way.
        LoadedManifestInfo loadedMainManifestInfo = load(
                new ManifestInfo(
                        mManifestFile.getName(),
                        mManifestFile,
                        mDocumentType,
                        Optional.<String>absent() /* mainManifestPackageName */),
                selectors,
                mergingReportBuilder);

        // first do we have a package declaration in the main manifest ?
        Optional<XmlAttribute> mainPackageAttribute =
                loadedMainManifestInfo.getXmlDocument().getPackage();
        if (mDocumentType != XmlDocument.Type.OVERLAY && !mainPackageAttribute.isPresent()) {
            mergingReportBuilder.addMessage(
                    loadedMainManifestInfo.getXmlDocument().getSourceFile(),
                    MergingReport.Record.Severity.ERROR,
                    String.format(
                            "Main AndroidManifest.xml at %1$s manifest:package attribute "
                                    + "is not declared",
                            loadedMainManifestInfo.getXmlDocument().getSourceFile()
                                    .print(true)));
            return mergingReportBuilder.build();
        }

        // load all the libraries xml files early to have a list of all possible node:selector
        // values.
        List<LoadedManifestInfo> loadedLibraryDocuments =
                loadLibraries(
                        selectors,
                        mergingReportBuilder,
                        mainPackageAttribute.isPresent()
                                ? mainPackageAttribute.get().getValue()
                                : null);

        // perform system property injection
        performSystemPropertiesInjection(mergingReportBuilder,
                loadedMainManifestInfo.getXmlDocument());

        // force the re-parsing of the xml as elements may have been added through system
        // property injection.
        loadedMainManifestInfo = new LoadedManifestInfo(loadedMainManifestInfo,
                loadedMainManifestInfo.getOriginalPackageName(),
                loadedMainManifestInfo.getXmlDocument().reparse());

        // invariant : xmlDocumentOptional holds the higher priority document and we try to
        // merge in lower priority documents.
        Optional<XmlDocument> xmlDocumentOptional = Optional.absent();
        for (File inputFile : mFlavorsAndBuildTypeFiles) {
            mLogger.verbose("Merging flavors and build manifest %s \n", inputFile.getPath());
            LoadedManifestInfo overlayDocument = load(
                    new ManifestInfo(null, inputFile, XmlDocument.Type.OVERLAY,
                            Optional.of(mainPackageAttribute.get().getValue())),
                    selectors,
                    mergingReportBuilder);

            // check package declaration.
            Optional<XmlAttribute> packageAttribute =
                    overlayDocument.getXmlDocument().getPackage();
            // if both files declare a package name, it should be the same.
            if (loadedMainManifestInfo.getOriginalPackageName().isPresent() &&
                    packageAttribute.isPresent()
                    && !loadedMainManifestInfo.getOriginalPackageName().get().equals(
                    packageAttribute.get().getValue())) {
                // no suggestion for library since this is actually forbidden to change the
                // the package name per flavor.
                String message = mMergeType == MergeType.APPLICATION
                        ? String.format(
                                "Overlay manifest:package attribute declared at %1$s value=(%2$s)\n"
                                        + "\thas a different value=(%3$s) "
                                        + "declared in main manifest at %4$s\n"
                                        + "\tSuggestion: remove the overlay declaration at %5$s "
                                        + "\tand place it in the build.gradle:\n"
                                        + "\t\tflavorName {\n"
                                        + "\t\t\tapplicationId = \"%2$s\"\n"
                                        + "\t\t}",
                                packageAttribute.get().printPosition(),
                                packageAttribute.get().getValue(),
                                mainPackageAttribute.get().getValue(),
                                mainPackageAttribute.get().printPosition(),
                                packageAttribute.get().getSourceFile().print(true))
                        : String.format(
                                "Overlay manifest:package attribute declared at %1$s value=(%2$s)\n"
                                        + "\thas a different value=(%3$s) "
                                        + "declared in main manifest at %4$s",
                                packageAttribute.get().printPosition(),
                                packageAttribute.get().getValue(),
                                mainPackageAttribute.get().getValue(),
                                mainPackageAttribute.get().printPosition());
                mergingReportBuilder.addMessage(
                        overlayDocument.getXmlDocument().getSourceFile(),
                        MergingReport.Record.Severity.ERROR,
                        message);
                return mergingReportBuilder.build();
            }

            overlayDocument.getXmlDocument().getRootNode().getXml().setAttribute("package",
                    mainPackageAttribute.get().getValue());
            xmlDocumentOptional = merge(xmlDocumentOptional, overlayDocument, mergingReportBuilder);

            if (!xmlDocumentOptional.isPresent()) {
                return mergingReportBuilder.build();
            }
        }

        mLogger.verbose("Merging main manifest %s\n", mManifestFile.getPath());
        xmlDocumentOptional =
                merge(xmlDocumentOptional, loadedMainManifestInfo, mergingReportBuilder);

        if (!xmlDocumentOptional.isPresent()) {
            return mergingReportBuilder.build();
        }

        // force main manifest package into resulting merged file when creating a library manifest.
        if (mMergeType == MergeType.LIBRARY) {
            // extract the package name...
            String mainManifestPackageName = loadedMainManifestInfo.getXmlDocument().getRootNode()
                    .getXml().getAttribute("package");
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(mainManifestPackageName)) {
                xmlDocumentOptional.get().getRootNode().getXml()
                        .setAttribute("package", mainManifestPackageName);
            }
        }
        for (LoadedManifestInfo libraryDocument : loadedLibraryDocuments) {
            mLogger.verbose("Merging library manifest " + libraryDocument.getLocation());
            xmlDocumentOptional = merge(
                    xmlDocumentOptional, libraryDocument, mergingReportBuilder);
            if (!xmlDocumentOptional.isPresent()) {
                return mergingReportBuilder.build();
            }
        }

        // done with proper merging phase, now we need to expand <nav-graph> elements, trim unwanted
        // elements, perform placeholder substitution and system properties injection.
        Map<String, NavigationXmlDocument> loadedNavigationMap = new HashMap<>();
        for (File navigationFile : mNavigationFiles) {
            String navigationId = navigationFile.getName().replaceAll("\\.xml$", "");
            if (loadedNavigationMap.get(navigationId) != null) {
                continue;
            }
            try (InputStream inputStream = mFileStreamProvider.getInputStream(navigationFile)) {
                loadedNavigationMap.put(
                        navigationId,
                        NavigationXmlLoader.INSTANCE.load(
                                navigationId, navigationFile, inputStream));
            } catch (Exception e) {
                throw new MergeFailureException(e);
            }
        }
        xmlDocumentOptional =
                Optional.of(
                        NavGraphExpander.INSTANCE.expandNavGraphs(
                                xmlDocumentOptional.get(),
                                loadedNavigationMap,
                                mergingReportBuilder));
        if (mergingReportBuilder.hasErrors()) {
            return mergingReportBuilder.build();
        }

        ElementsTrimmer.trim(xmlDocumentOptional.get(), mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()) {
            return mergingReportBuilder.build();
        }

        if (!mOptionalFeatures.contains(Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)) {
            // do one last placeholder substitution, this is useful as we don't stop the build
            // when a library failed a placeholder substitution, but the element might have
            // been overridden so the problem was transient. However, with the final document
            // ready, all placeholders values must have been provided.
            MergingReport.Record.Severity severity =
                    mMergeType == MergeType.LIBRARY
                            ? MergingReport.Record.Severity.INFO
                            : MergingReport.Record.Severity.ERROR;
            performPlaceHolderSubstitution(
                    loadedMainManifestInfo,
                    xmlDocumentOptional.get(),
                    mergingReportBuilder,
                    severity);
            if (mergingReportBuilder.hasErrors()) {
                return mergingReportBuilder.build();
            }
        }

        // perform system property injection.
        performSystemPropertiesInjection(mergingReportBuilder, xmlDocumentOptional.get());

        XmlDocument finalMergedDocument = xmlDocumentOptional.get();

        if (!mOptionalFeatures.contains(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)) {
            PostValidator.enforceToolsNamespaceDeclaration(finalMergedDocument);
        }

        PostValidator.validate(finalMergedDocument, mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()) {
            finalMergedDocument.getRootNode().addMessage(mergingReportBuilder,
                    MergingReport.Record.Severity.WARNING,
                    "Post merge validation failed");
        }

        finalMergedDocument.clearNodeNamespaces();

        // extract fully qualified class names before handling other optional features.
        if (mOptionalFeatures.contains(Invoker.Feature.EXTRACT_FQCNS)) {
            extractFqcns(finalMergedDocument);
        }

        // handle optional features which don't need access to XmlDocument layer.
        processOptionalFeatures(finalMergedDocument.getXml(), mergingReportBuilder);

        // call blame after other optional features handled.
        if (!mOptionalFeatures.contains(Invoker.Feature.SKIP_BLAME)) {
            try {
                mergingReportBuilder.setMergedDocument(
                        MergingReport.MergedManifestKind.BLAME,
                        mergingReportBuilder.blame(finalMergedDocument));
            } catch (Exception e) {
                mLogger.error(e, "Error while saving blame file, build will continue");
            }
        }

        mergingReportBuilder.setFinalPackageName(finalMergedDocument.getPackageName());
        mergingReportBuilder.setMergedXmlDocument(
                MergingReport.MergedManifestKind.MERGED, finalMergedDocument);

        MergingReport mergingReport = mergingReportBuilder.build();

        if (mReportFile.isPresent()) {
            writeReport(mergingReport);
        }

        return mergingReport;
    }

    /** Returns whether the given feature is enabled for this merger */
    public boolean hasFeature(@NonNull Invoker.Feature feature) {
        return mOptionalFeatures.contains(feature);
    }

    /**
     * Processes optional features which are not already handled in merge()
     *
     * @param document the resulting document after merging
     * @param mergingReport the merging report builder
     */
    private void processOptionalFeatures(
            @Nullable Document document, @NonNull MergingReport.Builder mergingReport) {
        if (document == null) {
            return;
        }

        // perform tools: annotations removal if requested.
        if (mOptionalFeatures.contains(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)) {
            ToolsInstructionsCleaner.cleanToolsReferences(mMergeType, document, mLogger);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.ADVANCED_PROFILING)) {
            addInternetPermission(document);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.TEST_ONLY)) {
            addTestOnlyAttribute(document);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.DEBUGGABLE)) {
            addDebuggableAttribute(document);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.ADD_MULTIDEX_APPLICATION_IF_NO_NAME)) {
            addMultiDexApplicationIfNoName(document);
        }

        if (!mOptionalFeatures.contains(Invoker.Feature.SKIP_XML_STRING)) {
            mergingReport.setMergedDocument(
                    MergingReport.MergedManifestKind.MERGED,
                    XmlPrettyPrinter.prettyPrint(
                            document,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(document.getDocumentElement()),
                            null, /* endOfLineSeparator */
                            false /* endWithNewLine */));
        }

        if (mOptionalFeatures.contains(Invoker.Feature.MAKE_AAPT_SAFE)) {
            PlaceholderEncoder.visit(document);
            mergingReport.setMergedDocument(
                    MergingReport.MergedManifestKind.AAPT_SAFE,
                    XmlPrettyPrinter.prettyPrint(
                            document,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(document.getDocumentElement()),
                            null, /* endOfLineSeparator */
                            false /* endWithNewLine */));
        }

        // Always save the pre InstantRun state in case some APT plugins require it.
        if (mOptionalFeatures.contains(Invoker.Feature.INSTANT_RUN_REPLACEMENT)) {
            instantRunReplacement(document);
            mergingReport.setMergedDocument(
                    MergingReport.MergedManifestKind.INSTANT_RUN,
                    XmlPrettyPrinter.prettyPrint(
                            document,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(document.getDocumentElement()),
                            null, /* endOfLineSeparator */
                            false /* endWithNewLine */));
        }

        if (mOptionalFeatures.contains(Invoker.Feature.ADD_FEATURE_SPLIT_INFO)) {
            addFeatureSplitAttributes(document, mFeatureName);
            mergingReport.setMergedDocument(
                    MergingReport.MergedManifestKind.MERGED,
                    XmlPrettyPrinter.prettyPrint(
                            document,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(document.getDocumentElement()),
                            null, /* endOfLineSeparator */
                            false /* endWithNewLine */));
        }

        if (mOptionalFeatures.contains(Invoker.Feature.TARGET_SANDBOX_VERSION)) {
            addTargetSandboxVersionAttribute(document);
            mergingReport.setMergedDocument(
                    MergingReport.MergedManifestKind.MERGED,
                    XmlPrettyPrinter.prettyPrint(
                            document,
                            XmlFormatPreferences.defaults(),
                            XmlFormatStyle.get(document.getDocumentElement()),
                            null, /* endOfLineSeparator */
                            false /* endWithNewLine */));
        }
    }

    /**
     * Set android:testOnly="true" to ensure APK will be rejected by the Play store.
     *
     * @param document the document for which the testOnly attribute should be set to true.
     */
    private static void addTestOnlyAttribute(@NonNull Document document) {
        Element manifest = document.getDocumentElement();
        ImmutableList<Element> applicationElements =
                getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION);
        if (!applicationElements.isEmpty()) {
            // assumes just 1 application element among manifest's immediate children.
            Element application = applicationElements.get(0);
            setAndroidAttribute(application, SdkConstants.ATTR_TEST_ONLY, SdkConstants.VALUE_TRUE);
        }
    }

    /**
     * Set android:debuggable="true"
     *
     * @param document the document for which the debuggable attribute should be set to true.
     */
    private static void addDebuggableAttribute(@NonNull Document document) {
        Element manifest = document.getDocumentElement();
        ImmutableList<Element> applicationElements =
                getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION);
        if (!applicationElements.isEmpty()) {
            // assumes just 1 application element among manifest's immediate children.
            Element application = applicationElements.get(0);
            setAndroidAttribute(application, SdkConstants.ATTR_DEBUGGABLE, SdkConstants.VALUE_TRUE);
        }
    }

    /**
     * Adds android:name="{@link SdkConstants#SUPPORT_MULTI_DEX_APPLICATION}" if there is no value
     * specified for that field.
     *
     * @param document the document for which the name attribute might be set.
     */
    private static void addMultiDexApplicationIfNoName(@NonNull Document document) {
        Element manifest = document.getDocumentElement();
        ImmutableList<Element> applicationElements =
                getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION);
        if (!applicationElements.isEmpty()) {
            Element application = applicationElements.get(0);
            setAndroidAttributeIfMissing(
                    application,
                    SdkConstants.ATTR_NAME,
                    SdkConstants.SUPPORT_MULTI_DEX_APPLICATION);
        }
    }

    /**
     * Set "android:splitName" attributes to <code>featureName</code> for every activity, service
     * and provider elements. Set "featureSplit" attribute to <code>featureName</code> for the
     * manifest element.
     *
     * @param document the document whose attributes are changed
     * @param featureName the value all of the changed attributes are set to
     */
    private static void addFeatureSplitAttributes(Document document, String featureName) {
        // first update attribute in manifest element
        Element manifest = document.getDocumentElement();
        if (manifest == null) {
            return;
        }

        String attributeName = SdkConstants.ATTR_FEATURE_SPLIT;
        manifest.setAttribute(attributeName, featureName);

        // then update attributes in the application element's child elements
        ImmutableList<Element> applicationElements =
                getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION);
        if (applicationElements.isEmpty()) {
            return;
        }

        // assumes just 1 application element among manifest's immediate children.
        Element application = applicationElements.get(0);
        List<String> elementNamesToUpdate =
                Arrays.asList(
                        SdkConstants.TAG_ACTIVITY,
                        SdkConstants.TAG_SERVICE,
                        SdkConstants.TAG_PROVIDER);
        for (String elementName : elementNamesToUpdate) {
            for (Element elementToUpdate : getChildElementsByName(application, elementName)) {
                setAndroidAttribute(elementToUpdate, SdkConstants.ATTR_SPLIT_NAME, featureName);
            }
        }
    }

    /**
     * Set "android:targetSandboxVersion" attribute to 2 for the manifest element.
     *
     * @param document the document whose attributes are changes
     */
    private static void addTargetSandboxVersionAttribute(@NonNull Document document) {
        Element manifest = document.getDocumentElement();
        if (manifest == null) {
            return;
        }
        setAndroidAttribute(manifest, SdkConstants.ATTR_TARGET_SANDBOX_VERSION, "2");
    }

    /**
     * Adds attributes and provider element to document's application element for instant run.
     *
     * @param document the document which gets edited.
     */
    private static void instantRunReplacement(@NonNull Document document) {
        Element manifest = document.getDocumentElement();
        ImmutableList<Element> applicationElements =
                getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION);
        if (applicationElements.isEmpty()) {
            throw new RuntimeException("Application not defined in AndroidManifest.xml");
        }
        // assumes just 1 application element among manifest's immediate children.
        Element application = applicationElements.get(0);
        setAttributeToTrue(application, SdkConstants.ATTR_ENABLED);
        setAttributeToTrue(application, SdkConstants.ATTR_HAS_CODE);
        addIrContentProvider(document, application);
    }

    /**
     * Adds internet permission to document if not already present.
     *
     * @param document the document which gets edited if necessary.
     */
    private static void addInternetPermission(@NonNull Document document) {
        String permission = "android.permission.INTERNET";
        Element manifest = document.getDocumentElement();
        ImmutableList<Element> usesPermissions =
                getChildElementsByName(manifest, SdkConstants.TAG_USES_PERMISSION);
        for (Element usesPermission : usesPermissions) {
            if (permission.equals(
                    usesPermission.getAttributeNS(
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))) {
                return;
            }
        }
        Element uses = document.createElement(SdkConstants.TAG_USES_PERMISSION);
        // Add the node to the document before setting the attribute to make sure
        // the namespace prefix is found correctly.
        document.getDocumentElement().appendChild(uses);
        setAndroidAttribute(uses, SdkConstants.ATTR_NAME, permission);
    }

    /**
     * Sets the element's attribute value to True.
     * @param element the xml element which attribute should be mutated.
     * @param attributeName the android namespace attribute name.
     */
    private static void setAttributeToTrue(Element element, String attributeName) {

        Attr enabledAttribute = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI, attributeName);

        // force it to be true.
        if (enabledAttribute != null) {
            element.setAttributeNS(
                    SdkConstants.ANDROID_URI,
                    enabledAttribute.getName(),
                    SdkConstants.VALUE_TRUE);
        }
    }

    /**
     * Adds a provider element as a child of the document's application element, as shown here:
     * <provider android:name="com.android.tools.ir.server.InstantRunContentProvider"
     * android:authorities="com.android.tools.ir.server.InstantRunContentProvider"
     * android:multiprocess="true" />
     *
     * @param document the document to add the provider element to
     * @param application the application element to add the provider element to
     */
    private static void addIrContentProvider(
            @NonNull Document document, @NonNull Element application) {
        Element cp = document.createElement(SdkConstants.TAG_PROVIDER);
        setAndroidAttribute(cp, SdkConstants.ATTR_NAME, BOOTSTRAP_INSTANT_RUN_CONTENT_PROVIDER);
        // Qualify authority as unique so that multiple IR app packages installed on a single
        // device do not conflict.
        String pkg = document.getDocumentElement().getAttribute(SdkConstants.ATTR_PACKAGE);
        if (pkg == null) {
            throw new RuntimeException("no package name set");
        }
        setAndroidAttribute(cp, SdkConstants.ATTR_AUTHORITIES, pkg + "." +
                            BOOTSTRAP_INSTANT_RUN_CONTENT_PROVIDER);
        // Multiprocess so we start in every process and decide on our own how to handle
        // having multiple processes
        setAndroidAttribute(cp, SdkConstants.ATTR_MULTIPROCESS, SdkConstants.VALUE_TRUE);
        application.appendChild(cp);
    }

    /**
     * Set an Android-namespaced XML attribute on the given node.
     *
     * @param node Node in which to set the attribute; must be part of a document
     * @param localName Non-prefixed attribute name
     * @param value value of the attribute
     */
    private static void setAndroidAttribute(Element node, String localName, String value) {
        String prefix =
                XmlUtils.lookupNamespacePrefix(
                        node, SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME, true);
        node.setAttributeNS(SdkConstants.ANDROID_URI, prefix + ":" + localName, value);
    }

    /**
     * Set an Android-namespaced XML attribute on the given node, if that attribute is missing.
     *
     * @param node Node in which to set the attribute; must be part of a document
     * @param localName Non-prefixed attribute name
     * @param value value of the attribute
     */
    private static void setAndroidAttributeIfMissing(Element node, String localName, String value) {
        if (!node.hasAttributeNS(SdkConstants.ANDROID_URI, localName)) {
            setAndroidAttribute(node, localName, value);
        }
    }

    /**
     * Returns a list of elements which are the immediate children of the given element and have the
     * given name.
     *
     * @param element the immediate parent of any elements in the returned list
     * @param name the name of any elements in the returned list
     * @return the list (possibly empty) of children elements with the given name
     */
    @NonNull
    private static ImmutableList<Element> getChildElementsByName(
            @NonNull Element element, @NonNull String name) {
        ImmutableList.Builder<Element> childListBuilder = ImmutableList.builder();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element && name.equals(childNode.getNodeName())) {
                childListBuilder.add((Element) childNode);
            }
        }
        return childListBuilder.build();
    }

    /**
     * Returns the {@link FileStreamProvider} used by this manifest merger. Use this to read files
     * if you need to access the content of a {@link XmlDocument}.
     */
    @SuppressWarnings("unused") // Allow future library usage, if necessary
    @NonNull
    public FileStreamProvider getFileStreamProvider() {
        return mFileStreamProvider;
    }

    /**
     * Creates the merging report file.
     * @param mergingReport the merging activities report to serialize.
     */
    private void writeReport(@NonNull MergingReport mergingReport) {
        FileWriter fileWriter = null;
        try {
            if (!mReportFile.get().getParentFile().exists()
                    && !mReportFile.get().getParentFile().mkdirs()) {
                mLogger.warning(String.format(
                        "Cannot create %1$s manifest merger report file,"
                                + "build will continue but merging activities "
                                + "will not be documented",
                        mReportFile.get().getAbsolutePath()));
            } else {
                fileWriter = new FileWriter(mReportFile.get());
                mergingReport.getActions().log(fileWriter);
            }
        } catch (IOException e) {
            mLogger.warning(String.format(
                    "Error '%1$s' while writing the merger report file, "
                            + "build can continue but merging activities "
                            + "will not be documented ",
                    e.getMessage()));
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    mLogger.warning(String.format(
                            "Error '%1$s' while closing the merger report file, "
                                    + "build can continue but merging activities "
                                    + "will not be documented ",
                            e.getMessage()));
                }
            }
        }
    }

    /**
     * shorten all fully qualified class name that belong to the same package as the manifest's
     * package attribute value.
     *
     * @param finalMergedDocument the AndroidManifest.xml document.
     */
    private static void extractFqcns(@NonNull XmlDocument finalMergedDocument) {
        extractFqcns(finalMergedDocument.getPackageName(), finalMergedDocument.getRootNode());
    }

    /**
     * shorten recursively all attributes that are package dependent of the passed nodes and all its
     * child nodes.
     *
     * @param packageName the manifest package name.
     * @param xmlElement the xml element to process recursively.
     */
    private static void extractFqcns(@NonNull String packageName, @NonNull XmlElement xmlElement) {
        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            if (xmlAttribute.getModel() != null && xmlAttribute.getModel().isPackageDependent()) {
                String value = xmlAttribute.getValue();
                if (value.startsWith(packageName) &&
                        value.charAt(packageName.length()) == '.') {
                    xmlAttribute.getXml().setValue(value.substring(packageName.length()));
                }
            }
        }
        for (XmlElement child : xmlElement.getMergeableElements()) {
            extractFqcns(packageName, child);
        }
    }

    /**
     * Load an xml file and perform placeholder substitution
     *
     * @param manifestInfo the android manifest information like if it is a library, an overlay or a
     *     main manifest file.
     * @param selectors all the libraries selectors
     * @param mergingReportBuilder the merging report to store events and errors.
     * @return a loaded manifest info.
     * @throws MergeFailureException if the merging cannot be completed successfully.
     */
    @NonNull
    private LoadedManifestInfo load(
            @NonNull ManifestInfo manifestInfo,
            @NonNull KeyResolver<String> selectors,
            @NonNull MergingReport.Builder mergingReportBuilder)
            throws MergeFailureException {

        File xmlFile = manifestInfo.mLocation;
        XmlDocument xmlDocument;
        try {
            InputStream inputStream = mFileStreamProvider.getInputStream(xmlFile);
            xmlDocument = XmlLoader.load(selectors,
                    mSystemPropertyResolver,
                    manifestInfo.mName,
                    xmlFile,
                    inputStream,
                    manifestInfo.getType(),
                    manifestInfo.getMainManifestPackageName());
        } catch (Exception e) {
            throw new MergeFailureException(e);
        }

        String originalPackageName = xmlDocument.getPackageName();
        MergingReport.Builder builder =
                manifestInfo.getType() == XmlDocument.Type.MAIN
                        ? mergingReportBuilder
                        : new MergingReport.Builder(mergingReportBuilder.getLogger(), this);

        // create updatedManifestInfo to have access to the packageName for
        // placeholder substitution if this is the MAIN manifest
        ManifestInfo updatedManifestInfo =
                manifestInfo.getType() == XmlDocument.Type.MAIN
                        ? new ManifestInfo(
                                manifestInfo.getName(),
                                manifestInfo.getLocation(),
                                manifestInfo.getType(),
                                Optional.of(originalPackageName))
                        : manifestInfo;
        // perform place holder substitution, this is necessary to do so early in case placeholders
        // are used in key attributes.
        MergingReport.Record.Severity severity =
                mMergeType == MergeType.LIBRARY
                        ? MergingReport.Record.Severity.INFO
                        : MergingReport.Record.Severity.ERROR;
        performPlaceHolderSubstitution(updatedManifestInfo, xmlDocument, builder, severity);

        builder.getActionRecorder().recordAddedNodeAction(xmlDocument.getRootNode(), false);

        return new LoadedManifestInfo(
                updatedManifestInfo, Optional.fromNullable(originalPackageName), xmlDocument);
    }

    private void performPlaceHolderSubstitution(
            @NonNull ManifestInfo manifestInfo,
            @NonNull XmlDocument xmlDocument,
            @NonNull MergingReport.Builder mergingReportBuilder,
            @NonNull MergingReport.Record.Severity severity) {

        if (mOptionalFeatures.contains(Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)) {
            return;
        }

        // check for placeholders presence, switch first the packageName and applicationId if
        // it is not explicitly set, unless dealing with a LIBRARY MergeType.
        // In case of a LIBRARY MergeType, we don't replace packageName or applicationId,
        // unless they're already specified in mPlaceHolderValues.
        Map<String, Object> finalPlaceHolderValues = mPlaceHolderValues;
        if (!mPlaceHolderValues.containsKey(PlaceholderHandler.APPLICATION_ID)
                && mMergeType != MergeType.LIBRARY
                && manifestInfo.getMainManifestPackageName().isPresent()) {
            String packageName = manifestInfo.getMainManifestPackageName().get();
            // add all existing placeholders except package name that will be swapped.
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            for (Map.Entry<String, Object> entry : mPlaceHolderValues.entrySet()) {
                if (!entry.getKey().equals(PlaceholderHandler.PACKAGE_NAME)) {
                    builder.put(entry);
                }
            }
            builder.put(PlaceholderHandler.PACKAGE_NAME, packageName);
            builder.put(PlaceholderHandler.APPLICATION_ID, packageName);
            finalPlaceHolderValues = builder.build();
        }

        KeyBasedValueResolver<String> placeHolderValueResolver =
                new MapBasedKeyBasedValueResolver<String>(finalPlaceHolderValues);
        PlaceholderHandler.visit(
                severity, xmlDocument, placeHolderValueResolver, mergingReportBuilder);
    }

    // merge the optionally existing xmlDocument with a lower priority xml file.
    private Optional<XmlDocument> merge(
            @NonNull Optional<XmlDocument> xmlDocument,
            @NonNull LoadedManifestInfo lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReportBuilder) throws MergeFailureException {

        MergingReport.Result validationResult = PreValidator
                .validate(mergingReportBuilder, lowerPriorityDocument.getXmlDocument());
        if (validationResult == MergingReport.Result.ERROR) {
            mergingReportBuilder.addMessage(
                    lowerPriorityDocument.getXmlDocument().getSourceFile(),
                    MergingReport.Record.Severity.ERROR,
                    "Validation failed, exiting");
            return Optional.absent();
        }

        Optional<XmlDocument> result;
        if (xmlDocument.isPresent()) {
            result = xmlDocument.get().merge(
                    lowerPriorityDocument.getXmlDocument(), mergingReportBuilder,
                    !mOptionalFeatures.contains(Invoker.Feature.NO_IMPLICIT_PERMISSION_ADDITION));
        } else {
            // exhaustiveSearch is true in recordAddedNodeAction() below because some of this
            // manifest's nodes might have already been recorded from the loading of
            // the main manifest, but we want to record any unrecorded descendants.
            // e.g., if the main manifest did not contain any meta-data nodes below its
            // application node, we still want to record the addition of any such
            // meta-data nodes this manifest contains.
            mergingReportBuilder
                    .getActionRecorder()
                    .recordAddedNodeAction(
                            lowerPriorityDocument.getXmlDocument().getRootNode(), true);
            result = Optional.of(lowerPriorityDocument.getXmlDocument());
        }

        // if requested, dump each intermediary merging stage into the report.
        if (mOptionalFeatures.contains(Invoker.Feature.KEEP_INTERMEDIARY_STAGES)
                && result.isPresent()) {
            mergingReportBuilder.addMergingStage(result.get().prettyPrint());
        }

        return result;
    }

    private List<LoadedManifestInfo> loadLibraries(
            @NonNull SelectorResolver selectors,
            @NonNull MergingReport.Builder mergingReportBuilder,
            @Nullable String mainManifestPackageName)
            throws MergeFailureException {

        ImmutableList.Builder<LoadedManifestInfo> loadedLibraryDocuments = ImmutableList.builder();
        for (Pair<String, File> libraryFile : Sets.newLinkedHashSet(mLibraryFiles)) {
            mLogger.verbose("Loading library manifest " + libraryFile.getSecond().getPath());
            ManifestInfo manifestInfo =
                    new ManifestInfo(
                            libraryFile.getFirst(),
                            libraryFile.getSecond(),
                            XmlDocument.Type.LIBRARY,
                            Optional.fromNullable(mainManifestPackageName));
            File xmlFile = manifestInfo.mLocation;
            XmlDocument libraryDocument;
            try {
                InputStream inputStream = mFileStreamProvider.getInputStream(xmlFile);
                libraryDocument = XmlLoader.load(selectors,
                        mSystemPropertyResolver,
                        manifestInfo.mName,
                        xmlFile,
                        inputStream,
                        XmlDocument.Type.LIBRARY,
                        Optional.<String>absent()  /* mainManifestPackageName */);
            } catch (Exception e) {
                throw new MergeFailureException(e);
            }
            // extract the package name...
            String libraryPackage = libraryDocument.getRootNode().getXml().getAttribute("package");
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(libraryPackage)) {
                selectors.addSelector(libraryPackage, libraryFile.getFirst());
            }

            // perform placeholder substitution, this is useful when the library is using
            // a placeholder in a key element, we however do not need to record these
            // substitutions so feed it with a fake merging report.
            MergingReport.Builder builder =
                    new MergingReport.Builder(mergingReportBuilder.getLogger(), this);
            builder.getActionRecorder().recordAddedNodeAction(libraryDocument.getRootNode(), false);
            performPlaceHolderSubstitution(
                    manifestInfo, libraryDocument, builder, MergingReport.Record.Severity.INFO);
            if (builder.hasErrors()) {
                // we log the errors but continue, in case the error is of no consequence
                // to the application consuming the library.
                builder.build().log(mLogger);
            }

            loadedLibraryDocuments.add(new LoadedManifestInfo(manifestInfo,
                    Optional.fromNullable(libraryDocument.getPackageName()),
                    libraryDocument));
        }
        return loadedLibraryDocuments.build();
    }

    /**
     * Creates a new {@link com.android.manifmerger.ManifestMerger2.Invoker} instance to invoke
     * the merging tool to merge manifest files for an application.
     *
     * @param mainManifestFile application main manifest file.
     * @param logger the logger interface to use.
     * @return an {@link com.android.manifmerger.ManifestMerger2.Invoker} instance that will allow
     * further customization and trigger the merging tool.
     */
    @NonNull
    public static Invoker newMerger(@NonNull File mainManifestFile,
            @NonNull ILogger logger,
            @NonNull MergeType mergeType) {
        return new Invoker(mainManifestFile, logger, mergeType, XmlDocument.Type.MAIN);
    }

    /**
     * Defines the merging type expected from the tool.
     */
    public enum MergeType {
        /**
         * Application merging type is used when packaging an application with a set of imported
         * libraries. The resulting merged android manifest is final and is not expected to be
         * imported in another application.
         */
        APPLICATION,

        /**
         * Library merging type is used when packaging a library. The resulting android manifest
         * file will not merge in all the imported libraries this library depends on. Also the tools
         * annotations will not be removed as they can be useful when later importing the resulting
         * merged android manifest into an application.
         */
        LIBRARY
    }

    /**
     * Defines a property that can add or override itself into an XML document.
     */
    public interface AutoAddingProperty {

        /**
         * Add itself (possibly just override the current value) with the passed value
         * @param actionRecorder to record actions.
         * @param document the xml document to add itself to.
         * @param value the value to set of this property.
         */
        void addTo(@NonNull ActionRecorder actionRecorder,
                @NonNull XmlDocument document,
                @NonNull String value);
    }

    /**
     * Perform {@link ManifestSystemProperty} injection.
     * @param mergingReport to log actions and errors.
     * @param xmlDocument the xml document to inject into.
     */
    protected void performSystemPropertiesInjection(
            @NonNull MergingReport.Builder mergingReport,
            @NonNull XmlDocument xmlDocument) {
        for (ManifestSystemProperty manifestSystemProperty : ManifestSystemProperty.values()) {
            String propertyOverride = mSystemPropertyResolver.getValue(manifestSystemProperty);
            if (propertyOverride != null) {
                manifestSystemProperty.addTo(
                        mergingReport.getActionRecorder(), xmlDocument, propertyOverride);
            }
        }
    }

    /**
     * A {@linkplain FileStreamProvider} provides (buffered, if necessary) {@link InputStream}
     * instances for a given {@link File} handle.
     */
    public static class FileStreamProvider {
        /**
         * Creates a reader for the given file -- which may not necessarily read the contents of the
         * file on disk. For example, in the IDE, the client will map the file handle to a document in
         * the editor, and read the current contents of that editor whether or not it has been saved.
         * <p>
         * This method is responsible for providing its own buffering, if necessary (e.g. when
         * reading from disk, make sure you wrap the file stream in a buffering input stream.)
         *
         * @param file the file handle
         * @return the contents of the file
         * @throws FileNotFoundException if the file handle is invalid
         */
        @SuppressWarnings("MethodMayBeStatic") // Intended for overrides outside this library
        protected InputStream getInputStream(@NonNull File file) throws FileNotFoundException {
            return new BufferedInputStream(new FileInputStream(file));
        }
    }

    /**
     * This class will hold all invocation parameters for the manifest merging tool.
     *
     * There are broadly three types of input to the merging tool :
     * <ul>
     *     <li>Build types and flavors overriding manifests</li>
     *     <li>Application main manifest</li>
     *     <li>Library manifest files</li>
     * </ul>
     *
     * Only the main manifest file is a mandatory parameter.
     *
     * High level description of the merging will be as follow :
     * <ol>
     *     <li>Build type and flavors will be merged first in the order they were added. Highest
     *     priority file added first, lowest added last.</li>
     *     <li>Resulting document is merged with lower priority application main manifest file.</li>
     *     <li>Resulting document is merged with each library file manifest file in the order
     *     they were added. Highest priority added first, lowest added last.</li>
     *     <li>Resulting document is returned as results of the merging process.</li>
     * </ol>
     *
     */
    public static class Invoker<T extends Invoker<T>>{

        protected final File mMainManifestFile;

        protected final ImmutableMap.Builder<ManifestSystemProperty, Object> mSystemProperties =
                new ImmutableMap.Builder<ManifestSystemProperty, Object>();

        @NonNull
        protected final ILogger mLogger;
        @NonNull
        protected final ImmutableMap.Builder<String, Object> mPlaceholders =
                new ImmutableMap.Builder<String, Object>();
        @NonNull
        private final ImmutableList.Builder<Pair<String, File>> mLibraryFilesBuilder =
                new ImmutableList.Builder<Pair<String, File>>();
        @NonNull
        private final ImmutableList.Builder<File> mFlavorsAndBuildTypeFiles =
                new ImmutableList.Builder<File>();
        @NonNull
        private final ImmutableList.Builder<Feature> mFeaturesBuilder =
                new ImmutableList.Builder<Feature>();
        @NonNull
        private final MergeType mMergeType;
        @NonNull private  XmlDocument.Type mDocumentType;
        @Nullable private File mReportFile;

        @Nullable
        private FileStreamProvider mFileStreamProvider;

        @NonNull private String mFeatureName;

        @NonNull
        private final ImmutableList.Builder<File> mNavigationFilesBuilder =
                new ImmutableList.Builder<>();

        /**
         * Sets a value for a {@link ManifestSystemProperty}
         * @param override the property to set
         * @param value the value for the property
         * @return itself.
         */
        @NonNull
        public Invoker setOverride(@NonNull ManifestSystemProperty override, @NonNull String value) {
            mSystemProperties.put(override, value);
            return thisAsT();
        }

        /**
         * Adds placeholders names and associated values for substitution.
         * @return itself.
         */
        @NonNull
        public Invoker setPlaceHolderValues(@NonNull Map<String, Object> keyValuePairs) {
            mPlaceholders.putAll(keyValuePairs);
            return thisAsT();
        }

        /**
         * Adds a new placeholder name and value for substitution.
         * @return itself.
         */
        @NonNull
        public Invoker setPlaceHolderValue(@NonNull String placeHolderName, @NonNull String value) {
            mPlaceholders.put(placeHolderName, value);
            return thisAsT();
        }

        /**
         * Optional behavior of the merging tool can be turned on by setting these Feature.
         */
        public enum Feature {

            /**
             * Keep all intermediary merged files during the merging process. This is particularly
             * useful for debugging/tracing purposes.
             */
            KEEP_INTERMEDIARY_STAGES,

            /**
             * When logging file names, use {@link java.io.File#getName()} rather than
             * {@link java.io.File#getPath()}
             */
            PRINT_SIMPLE_FILENAMES,

            /**
             * Perform a sweep after all merging activities to remove all fully qualified class
             * names and replace them with the equivalent short version.
             */
            EXTRACT_FQCNS,

            /**
             * Perform a sweep after all merging activities to remove all tools: decorations.
             */
            REMOVE_TOOLS_DECLARATIONS,

            /**
             * Do no perform placeholders replacement.
             */
            NO_PLACEHOLDER_REPLACEMENT,

            /**
             * Encode unresolved placeholders to be AAPT friendly.
             */
            MAKE_AAPT_SAFE,

            /**
             * Perform InstantRun related swapping in the merged manifest file.
             */
            INSTANT_RUN_REPLACEMENT,

            /**
             * Clients will not request the blame history
             */
            SKIP_BLAME,

            /**
             * Clients will only request the merged XML documents, not XML pretty printed documents
             */
            SKIP_XML_STRING,

            /**
             * Add android:testOnly="true" attribute to prevent APK from being uploaded to Play
             * store.
             */
            TEST_ONLY,

            /**
             * Do not perform implicit permission addition.
             */
            NO_IMPLICIT_PERMISSION_ADDITION,

            /** Perform Studio advanced profiling manifest modifications */
            ADVANCED_PROFILING,

            /** Add feature split information */
            ADD_FEATURE_SPLIT_INFO,

            /** Set the android:debuggable flag to the application. */
            DEBUGGABLE,

            /** Set the android:targetSandboxVersion attribute. */
            TARGET_SANDBOX_VERSION,

            /**
             * When there are attribute value conflicts, automatically pick the higher priority
             * value.
             *
             * <p>This is for example used in the IDE when we need to merge a new manifest template
             * into an existing one and we don't want to abort the merge.
             *
             * <p>(This will log a warning.)
             */
            HANDLE_VALUE_CONFLICTS_AUTOMATICALLY,

            /**
             * Adds {@link SdkConstants#SUPPORT_MULTI_DEX_APPLICATION} as application name if none
             * is specified. Used for legacy multidex.
             */
            ADD_MULTIDEX_APPLICATION_IF_NO_NAME,
        }

        /**
         * Creates a new builder with the mandatory main manifest file.
         * @param mainManifestFile application main manifest file.
         * @param logger the logger interface to use.
         */
        private Invoker(
                @NonNull File mainManifestFile,
                @NonNull ILogger logger,
                @NonNull MergeType mergeType,
                @NonNull XmlDocument.Type documentType) {
            this.mMainManifestFile = Preconditions.checkNotNull(mainManifestFile);
            this.mLogger = logger;
            this.mMergeType = mergeType;
            this.mDocumentType = documentType;
            this.mFeatureName = "";
        }

        /**
         * Sets the file to use to write the merging report. If not called,
         * the merging process will not write a report.
         * @param mergeReport the file to write the report in.
         * @return itself.
         */
        @NonNull
        public Invoker setMergeReportFile(@Nullable File mergeReport) {
            mReportFile = mergeReport;
            return this;
        }

        /**
         * Add one library file manifest, will be added last in the list of library files which will
         * make the parameter the lowest priority library manifest file.
         * @param file the library manifest file to add.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifest(@NonNull File file) {
            addLibraryManifest(file.getName(), file);
            return thisAsT();
        }

        /**
         * Add one library file manifest, will be added last in the list of library files which will
         * make the parameter the lowest priority library manifest file.
         * @param file the library manifest file to add.
         * @param name the library name.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifest(@NonNull String name, @NonNull File file) {
            if (mMergeType == MergeType.LIBRARY) {
                throw new IllegalStateException(
                  "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.add(Pair.of(name, file));
            return thisAsT();
        }

        /**
         * Sets library dependencies for this merging activity.
         * @param namesAndFiles the list of library dependencies.
         * @return itself.
         *
         * @deprecated use addLibraryManifest or addAndroidBundleManifests
         */
        @NonNull
        @Deprecated
        public Invoker addBundleManifests(@NonNull List<Pair<String, File>> namesAndFiles) {
            if (mMergeType == MergeType.LIBRARY && !namesAndFiles.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.addAll(namesAndFiles);
            return thisAsT();
        }

        /**
         * Sets manifest providers for this merging activity.
         * @param providers the list of manifest providers.
         * @return itself.
         */
        @NonNull
        public Invoker addManifestProviders(@NonNull Iterable<? extends ManifestProvider> providers) {
            for (ManifestProvider provider : providers) {
                mLibraryFilesBuilder.add(Pair.of(provider.getName(), provider.getManifest()));
            }
            return thisAsT();
        }

        /**
         * Add several library file manifests at then end of the list which will make them the
         * lowest priority manifest files. The relative priority between all the files passed as
         * parameters will be respected.
         * @param files library manifest files to add last.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifests(@NonNull File... files) {
            for (File file : files) {
                addLibraryManifest(file);
            }
            return thisAsT();
        }

        /**
         * Add a flavor or build type manifest file last in the list.
         * @param file build type or flavor manifest file
         * @return itself.
         */
        @NonNull
        public Invoker addFlavorAndBuildTypeManifest(@NonNull File file) {
            this.mFlavorsAndBuildTypeFiles.add(file);
            return thisAsT();
        }

        /**
         * Add several flavor or build type manifest files last in the list. Relative priorities
         * between the passed files as parameters will be respected.
         * @param files build type of flavor manifest files to add.
         * @return itself.
         */
        @NonNull
        public Invoker addFlavorAndBuildTypeManifests(File... files) {
            this.mFlavorsAndBuildTypeFiles.add(files);
            return thisAsT();
        }

        /**
         * Sets some optional features for the merge tool.
         *
         * @param features one to many features to set.
         * @return itself.
         */
        @NonNull
        public Invoker withFeatures(Feature...features) {
            mFeaturesBuilder.add(features);
            return thisAsT();
        }

        /**
         * Sets a file stream provider which allows the client of the manifest merger to provide
         * arbitrary content lookup for files. <p> NOTE: There should only be one.
         *
         * @param provider the provider to use
         * @return itself.
         */
        @NonNull
        public Invoker withFileStreamProvider(@Nullable FileStreamProvider provider) {
            assert mFileStreamProvider == null || provider == null;
            mFileStreamProvider = provider;
            return thisAsT();
        }

        /** Regular expression defining legal feature split name. */
        private static final Pattern FEATURE_NAME_PATTERN =
                Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_]*");

        /**
         * Specify the feature name for feature merging.
         *
         * @param featureName the feature name to use.
         * @return itself.
         */
        @NonNull
        public Invoker setFeatureName(@Nullable String featureName) {
            if (featureName != null) {
                mFeatureName = featureName;
                if (!FEATURE_NAME_PATTERN.matcher(mFeatureName).matches()) {
                    throw new IllegalArgumentException(
                            "FeatureName must follow "
                                    + FEATURE_NAME_PATTERN.pattern()
                                    + " regex, found "
                                    + featureName);
                }
            }
            return thisAsT();
        }

        /**
         * Add one navigation file. It will be added last in the list of navigation files which will
         * make it the lowest priority navigation file.
         *
         * @param file the navigation file to add.
         * @return itself.
         */
        @NonNull
        public Invoker addNavigationFile(@NonNull File file) {
            this.mNavigationFilesBuilder.add(file);
            return thisAsT();
        }

        /**
         * Add several navigation files last in the list. Relative priorities between the passed
         * files as parameters will be respected.
         *
         * @param files the navigation files to add.
         * @return itself.
         */
        @NonNull
        public Invoker addNavigationFiles(@NonNull Iterable<File> files) {
            this.mNavigationFilesBuilder.addAll(files);
            return thisAsT();
        }

        /**
         * Specify if the file being merged is an overlay (flavor). If not called, the merging
         * process will assume a master manifest merge. The master manifest needs to have a package
         * and some other mandatory fields like "uses-sdk", etc.
         *
         * @return itself.
         */
        @NonNull
        public Invoker asType(XmlDocument.Type type) {
            mDocumentType = type;
            return this;
        }

        /**
         * Perform the merging and return the result.
         *
         * @return an instance of {@link com.android.manifmerger.MergingReport} that will give
         * access to all the logging and merging records.
         *
         * This method can be invoked several time and will re-do the file merges.
         *
         * @throws com.android.manifmerger.ManifestMerger2.MergeFailureException if the merging
         * cannot be completed successfully.
         */
        @NonNull
        public MergingReport merge() throws MergeFailureException {

            // provide some free placeholders values.
            ImmutableMap<ManifestSystemProperty, Object> systemProperties = mSystemProperties.build();
            if (systemProperties.containsKey(ManifestSystemProperty.PACKAGE)) {
                // if the package is provided, make it available for placeholder replacement.
                mPlaceholders.put(PACKAGE_NAME, systemProperties.get(ManifestSystemProperty.PACKAGE));
                // as well as applicationId since package system property overrides everything
                // but not when output is a library since only the final (application)
                // application Id should be used to replace libraries "applicationId" placeholders.
                if (mMergeType != MergeType.LIBRARY) {
                    mPlaceholders.put(APPLICATION_ID, systemProperties.get(ManifestSystemProperty.PACKAGE));
                }
            }

            FileStreamProvider fileStreamProvider = mFileStreamProvider != null
                    ? mFileStreamProvider : new FileStreamProvider();
            ManifestMerger2 manifestMerger =
                    new ManifestMerger2(
                            mLogger,
                            mMainManifestFile,
                            mLibraryFilesBuilder.build(),
                            mFlavorsAndBuildTypeFiles.build(),
                            mFeaturesBuilder.build(),
                            mPlaceholders.build(),
                            new MapBasedKeyBasedValueResolver<ManifestSystemProperty>(
                                    systemProperties),
                            mMergeType,
                            mDocumentType,
                            Optional.fromNullable(mReportFile),
                            mFeatureName,
                            fileStreamProvider,
                            mNavigationFilesBuilder.build());
            return manifestMerger.merge();
        }

        @NonNull
        @SuppressWarnings("unchecked")
        private T thisAsT() {
            return (T) this;
        }
    }

    /**
     * Helper class for map based placeholders key value pairs.
     */
    public static class MapBasedKeyBasedValueResolver<T> implements KeyBasedValueResolver<T> {

        private final ImmutableMap<T, Object> keyValues;

        public MapBasedKeyBasedValueResolver(@NonNull Map<T, Object> keyValues) {
            this.keyValues = ImmutableMap.copyOf(keyValues);
        }

        @Nullable
        @Override
        public String getValue(@NonNull T key) {
            Object value = keyValues.get(key);
            return value == null ? null : value.toString();
        }
    }

    private static class ManifestInfo {

        private ManifestInfo(
                String name,
                File location,
                XmlDocument.Type type,
                Optional<String> mainManifestPackageName) {
            mName = name;
            mLocation = location;
            mType = type;
            mMainManifestPackageName = mainManifestPackageName;
        }

        private final String mName;
        private final File mLocation;
        private final XmlDocument.Type mType;
        private final Optional<String> mMainManifestPackageName;

        String getName() {
            return mName;
        }

        File getLocation() {
            return mLocation;
        }

        XmlDocument.Type getType() {
            return mType;
        }

        Optional<String> getMainManifestPackageName() {
            return mMainManifestPackageName;
        }
    }

    private static class LoadedManifestInfo extends ManifestInfo {

        @NonNull private final XmlDocument mXmlDocument;
        @NonNull private final Optional<String> mOriginalPackageName;

        private LoadedManifestInfo(@NonNull ManifestInfo manifestInfo,
                @NonNull Optional<String> originalPackageName,
                @NonNull XmlDocument xmlDocument) {
            super(manifestInfo.mName,
                    manifestInfo.mLocation,
                    manifestInfo.mType,
                    manifestInfo.getMainManifestPackageName());
            mXmlDocument = xmlDocument;
            mOriginalPackageName = originalPackageName;
        }

        @NonNull
        public XmlDocument getXmlDocument() {
            return mXmlDocument;
        }

        @NonNull
        public Optional<String> getOriginalPackageName() {
            return mOriginalPackageName;
        }
    }

    /**
     * Implementation a {@link com.android.manifmerger.KeyResolver} capable of resolving all
     * selectors value in the context of the passed libraries to this merging activities.
     */
    static class SelectorResolver implements KeyResolver<String> {

        private final Map<String, String> mSelectors = new HashMap<String, String>();

        protected void addSelector(String key, String value) {
            mSelectors.put(key, value);
        }

        @Nullable
        @Override
        public String resolve(String key) {
            return mSelectors.get(key);
        }

        @NonNull
        @Override
        public Iterable<String> getKeys() {
            return mSelectors.keySet();
        }
    }

    // a wrapper exception to all sorts of failure exceptions that can be thrown during merging.
    public static class MergeFailureException extends Exception {

        protected MergeFailureException(Exception cause) {
            super(cause);
        }
    }
}
