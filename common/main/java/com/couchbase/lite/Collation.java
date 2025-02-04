//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;


/**
 * Collation defines how strings are compared and is used when creating a COLLATE expression.
 * The COLLATE expression can be used in the WHERE clause when comparing two strings or in the
 * ORDER BY clause when specifying how the order of the query results. CouchbaseLite provides
 * two types of the Collation, ASCII and Unicode. Without specifying the COLLATE expression
 * Couchbase Lite will use the ASCII with case sensitive collation by default.
 */
public class Collation {

    /**
     * ASCII collation compares two strings by using binary comparison.
     */
    public static final class ASCII extends Collation {
        ASCII() { super(false, null); }

        /**
         * Specifies whether the collation is case-insensitive or not. Case-insensitive
         * collation will treat ASCII uppercase and lowercase letters as equivalent.
         *
         * @param ignCase True for case-insensitive; false for case-sensitive.
         * @return The Unicode Collation object.
         */
        @NonNull
        public ASCII setIgnoreCase(boolean ignCase) { return (ASCII) super.setIgnoreCase(ignCase); }
    }

    /**
     * <a href="http://userguide.icu-project.org/collation">Unicode Collation</a> that will compare two strings
     * by using Unicode collation algorithm. If the locale is not specified, the collation is
     * Unicode-aware but not localized; for example, accented Roman letters sort right after
     * the base letter
     */
    public static final class Unicode extends Collation {
        // NOTE: System.getProperty("user.country") returns null for country code
        Unicode() { super(true, System.getProperty("user.language")); }

        /**
         * Specifies the locale to allow the collation to compare strings appropriately based on
         * the locale.  The local code is an [ISO-639](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes)
         * language code plus, optionally, an underscore and an
         * [ISO-3166](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
         * country code: "en", "en_US", "fr_CA", etc.
         *
         * @param locale The locale code
         * @return this
         */
        @NonNull
        public Unicode setLocale(@Nullable String locale) { return (Unicode) super.setLocale(locale); }

        /**
         * Specifies whether the collation ignore the accents or diacritics when
         * comparing the strings or not.
         *
         * @param ignAccents True for accent-insensitive; false for accent-sensitive.
         * @return The Unicode Collation object.
         */
        @NonNull
        public Unicode setIgnoreAccents(boolean ignAccents) { return (Unicode) super.setIgnoreAccents(ignAccents); }

        /**
         * Specifies whether the collation is case-insensitive or not. Case-insensitive
         * collation will treat ASCII uppercase and lowercase letters as equivalent.
         *
         * @param ignCase True for case-insensitive; false for case-sensitive.
         * @return The Unicode Collation object.
         */
        @NonNull
        public Unicode setIgnoreCase(boolean ignCase) { return (Unicode) super.setIgnoreCase(ignCase); }
    }

    /**
     * Creates an ASCII collation that will compare two strings by using binary comparison.
     *
     * @return The ASCII collation.
     */
    @NonNull
    public static ASCII ascii() { return new ASCII(); }

    /**
     * Creates a Unicode collation that will compare two strings by using Unicode Collation
     * Algorithm. If the locale is not specified, the collation is Unicode-aware but
     * not localized; for example, accented Roman letters sort right after the base letter
     *
     * @return The Unicode collation.
     */
    @NonNull
    public static Unicode unicode() { return new Unicode(); }

    private final boolean isUnicode;

    @Nullable
    private String locale;
    private boolean ignoreAccents;
    private boolean ignoreCase;

    protected Collation(boolean isUnicode, @Nullable String locale) {
        this.isUnicode = isUnicode;
        this.locale = locale;
    }

    @NonNull
    protected Collation setLocale(@Nullable String locale) {
        this.locale = locale;
        return this;
    }

    @NonNull
    protected Collation setIgnoreAccents(boolean ignoreAccents) {
        this.ignoreAccents = ignoreAccents;
        return this;
    }

    @NonNull
    protected Collation setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return "Collation{" + locale + ", " + isUnicode + ", " + ignoreAccents + ", " + ignoreCase + '}';
    }

    @NonNull
    Object asJSON() {
        final Map<String, Object> json = new HashMap<>();
        json.put("UNICODE", isUnicode);
        json.put("LOCALE", locale);
        json.put("CASE", !ignoreCase);
        json.put("DIAC", !ignoreAccents);
        return json;
    }
}
