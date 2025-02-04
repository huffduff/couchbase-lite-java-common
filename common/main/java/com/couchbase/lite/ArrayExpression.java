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

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Array expression
 */
public final class ArrayExpression {
    enum QuantifiesType {ANY, ANY_AND_EVERY, EVERY}

    /**
     * Creates an ANY Quantified operator (ANY &lt;variable name&gt; IN &lt;expr&gt; SATISFIES &lt;expr&gt;)
     * with the given variable name. The method returns an IN clause object that is used for
     * specifying an array object or an expression evaluated as an array object, each item of
     * which will be evaluated against the satisfies expression.
     * The ANY operator returns TRUE if at least one of the items in the array satisfies the given
     * satisfies expression.
     *
     * @param variable The variable expression.
     * @return An In object
     */
    @NonNull
    public static ArrayExpressionIn any(@NonNull VariableExpression variable) {
        Preconditions.assertNotNull(variable, "variable");
        return new ArrayExpressionIn(QuantifiesType.ANY, variable);
    }

    /**
     * Creates an EVERY Quantified operator (EVERY &lt;variable name&gt; IN &lt;expr&gt; SATISFIES &lt;expr&gt;)
     * with the given variable name. The method returns an IN clause object
     * that is used for specifying an array object or an expression evaluated as an array object,
     * each of which will be evaluated against the satisfies expression.
     * The EVERY operator returns TRUE if the array is empty OR every item in the array
     * satisfies the given satisfies expression.
     *
     * @param variable The variable expression.
     * @return An In object.
     */
    @NonNull
    public static ArrayExpressionIn every(@NonNull VariableExpression variable) {
        Preconditions.assertNotNull(variable, "variable");
        return new ArrayExpressionIn(QuantifiesType.EVERY, variable);
    }

    /**
     * Creates an ANY AND EVERY Quantified operator (ANY AND EVERY &lt;variable name&gt; IN &lt;expr&gt;
     * SATISFIES &lt;expr&gt;) with the given variable name. The method returns an IN clause object
     * that is used for specifying an array object or an expression evaluated as an array object,
     * each of which will be evaluated against the satisfies expression.
     * The ANY AND EVERY operator returns TRUE if the array is NOT empty, and at least one of
     * the items in the array satisfies the given satisfies expression.
     *
     * @param variable The variable expression.
     * @return An In object.
     */
    @NonNull
    public static ArrayExpressionIn anyAndEvery(@NonNull VariableExpression variable) {
        Preconditions.assertNotNull(variable, "variable");
        return new ArrayExpressionIn(QuantifiesType.ANY_AND_EVERY, variable);
    }

    /**
     * Creates a variable expression. The variable are used to represent each item in an array in the
     * quantified operators (ANY/ANY AND EVERY/EVERY &lt;variable name&gt; IN &lt;expr&gt; SATISFIES &lt;expr&gt;)
     * to evaluate expressions over an array.
     *
     * @param name The variable name
     * @return A variable expression
     */
    @NonNull
    public static VariableExpression variable(@NonNull String name) {
        Preconditions.assertNotNull(name, "name");
        return new VariableExpression(name);
    }

    private ArrayExpression() { }
}
