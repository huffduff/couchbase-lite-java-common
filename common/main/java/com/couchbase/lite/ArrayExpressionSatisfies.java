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

import java.util.ArrayList;
import java.util.List;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The Satisfies class represents the SATISFIES clause object in a quantified operator
 * (ANY/ANY AND EVERY/EVERY &lt;variable name&gt; IN &lt;expr&gt; SATISFIES &lt;expr&gt;).
 * The SATISFIES clause is used for specifying an expression that will be used to evaluate
 * each item in the array.
 */
public final class ArrayExpressionSatisfies {
    private static final class QuantifiedExpression extends Expression {
        @NonNull
        private final ArrayExpression.QuantifiesType type;
        @NonNull
        private final VariableExpression variable;
        @NonNull
        private final Expression inExpression;
        @NonNull
        private final Expression satisfiedExpression;

        QuantifiedExpression(
            @NonNull ArrayExpression.QuantifiesType type,
            @NonNull VariableExpression variable,
            @NonNull Expression inExpression,
            @NonNull Expression satisfiesExpression) {
            this.type = type;
            this.variable = variable;
            this.inExpression = inExpression;
            this.satisfiedExpression = satisfiesExpression;
        }

        @NonNull
        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>(4);

            // type
            switch (type) {
                case ANY:
                    json.add("ANY");
                    break;
                case ANY_AND_EVERY:
                    json.add("ANY AND EVERY");
                    break;
                case EVERY:
                    json.add("EVERY");
                    break;
            }

            // variable
            json.add(variable.getName());

            // in Expression
            json.add(inExpression.asJSON());

            // satisfies Expression
            json.add(satisfiedExpression.asJSON());

            return json;
        }
    }


    @NonNull
    private final ArrayExpression.QuantifiesType type;
    @NonNull
    private final VariableExpression variable;
    @NonNull
    private final Expression inExpression;

    ArrayExpressionSatisfies(
        @NonNull ArrayExpression.QuantifiesType type,
        @NonNull VariableExpression variable,
        @NonNull Expression inExpression) {
        this.type = type;
        this.variable = variable;
        this.inExpression = inExpression;
    }

    /**
     * Creates a complete quantified operator with the given satisfies expression.
     *
     * @param expression Parameter expression: The satisfies expression used for evaluating each item in the array.
     * @return The quantified expression.
     */
    @NonNull
    public Expression satisfies(@NonNull Expression expression) {
        Preconditions.assertNotNull(expression, "expression");
        return new QuantifiedExpression(type, variable, inExpression, expression);
    }
}
