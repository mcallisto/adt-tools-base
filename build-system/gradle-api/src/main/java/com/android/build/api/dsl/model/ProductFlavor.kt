/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.api.dsl.model

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating
import org.gradle.api.Named

/**
 * Encapsulates all product flavors properties for this project.
 *
 * Product flavors represent different versions of your project that you expect to co-exist on a
 * single device, the Google Play store, or repository. For example, you can configure 'demo' and
 * 'full' product flavors for your app, and each of those flavors can specify different features,
 * device requirements, resources, and application ID's--while sharing common source code and
 * resources. So, product flavors allow you to output different versions of your project by simply
 * changing only the components and settings that are different between them.
 *
 * Configuring product flavors is similar to
 * [configuring build types](https://d.android.com/studio/build/build-variants.html#build-types):
 * add them to the [`productFlavors`][com.android.build.api.dsl.model.ProductFlavor] block of your
 * module's `build.gradle` file and configure the settings you want. Product flavors support the same
 * properties as the [`defaultConfig`][com.android.build.api.dsl.model.DefaultConfig] block—this is
 * because `defaultConfig` defines a `ProductFlavor` object that the plugin uses as
 * the base configuration for all other flavors. Each flavor you configure can then override any of
 * the default values in `defaultConfig`, such as the
 * [`applicationId`][com.android.build.api.dsl.model.ProductFlavorOrVariant.applicationId].
 *
 * When [using Android plugin 3.0.0 and higher](https://d.android.com/studio/build/gradle-plugin-3-0-0-migration.html),
 * _each flavor must belong to a [`dimension`][com.android.build.api.dsl.model.ProductFlavor.dimension]_.
 *
 * When you
 * [configure product flavors](https://d.android.com/studio/build/build-variants.html#product-flavors),
 * the Android plugin automatically combines them with your
 * [`buildType`][com.android.build.api.dsl.model.BuildType] configurations to
 * [create build variants](https://developer.android.com/studio/build/build-variants.html).
 * If the plugin creates certain build variants that you don't want, you can use the
 * [`VariantFilter`][com.android.build.api.variant.VariantFilter] API to
 * [filter variants](https://d.android.com/studio/build/build-variants.html#filter-variants).
 */

@Incubating
interface ProductFlavor : BaseFlavor, BuildTypeOrProductFlavor, ProductFlavorOrVariant, VariantProperties, FallbackStrategy, Initializable<ProductFlavor>, Named {
    /**
     * Specifies the flavor dimension that this product flavor belongs to.
     *
     * When configuring product flavors with Android plugin 3.0.0 and higher, you must specify at
     * least one flavor dimension, using the
     * [`flavorDimensions`][com.android.build.api.dsl.extension.VariantAwareProperties.flavorDimensions]
     * property, and then assign each flavor to a dimension. Otherwise, you will get the following
     * build error:
     *
     * ```
     * Error:All flavors must now belong to a named flavor dimension.
     * The flavor 'flavor_name' is not assigned to a flavor dimension.
     * ```
     *
     * By default, when you specify only one dimension, all flavors you configure automatically
     * belong to that dimension. If you specify more than one dimension, you need to manually assign
     * each flavor to a dimension, as shown in the sample below:
     *
     * ```
     * android {
     *     ...
     *     // Specifies the flavor dimensions you want to use. The order in which you
     *     // list each dimension determines its priority, from highest to lowest,
     *     // when Gradle merges variant sources and configurations. You must assign
     *     // each product flavor you configure to one of the flavor dimensions.
     *     flavorDimensions 'api', 'version'
     *
     *     productFlavors {
     *       demo {
     *         // Assigns this product flavor to the 'version' flavor dimension.
     *         dimension 'version'
     *         ...
     *     }
     *
     *       full {
     *         dimension 'version'
     *         ...
     *       }
     *
     *       minApi24 {
     *         // Assigns this flavor to the 'api' dimension.
     *         dimension 'api'
     *         minSdkVersion '24'
     *         versionNameSuffix "-minApi24"
     *         ...
     *       }
     *
     *       minApi21 {
     *         dimension "api"
     *         minSdkVersion '21'
     *         versionNameSuffix "-minApi21"
     *         ...
     *       }
     *    }
     * }
     * ```
     *
     * To learn more about configuring flavor dimensions, read
     * [Combine multiple flavors](https://developer.android.com/studio/build/build-variants.html#flavor-dimensions).
     *
     * @see [`flavorDimensions`][com.android.build.api.dsl.extension.VariantAwareProperties.flavorDimensions]
     * @return The value of the dimension the product flavor belongs to.
     */
    var dimension: String?
}
