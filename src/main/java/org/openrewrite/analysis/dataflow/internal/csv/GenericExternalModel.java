/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.analysis.dataflow.internal.csv;

import lombok.Data;
import lombok.Getter;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.SimpleMethodMatcher;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface GenericExternalModel extends SimpleMethodMatcher {

    String getNamespace();
    String getType();
    boolean isSubtypes();
    String getName();
    String getSignature();

    String getArguments();

    default String getFullyQualifiedName() {
        return getNamespace() + "." + getType();
    }

    default boolean isConstructor() {
        // If the type and the name are the same, then this the signature for a constructor
        return this.getType().equals(this.getName());
    }

    @Override
    default boolean matchesTargetType(JavaType.@Nullable FullyQualified type) {
        return true;
    }

    @Override
    default boolean matchesMethodName(String methodName) {
        if (isConstructor()) {
            return "<constructor>".equals(methodName);
        }
        return getName().equals(methodName);
    }

    @Override
    default boolean matchesParameterTypes(List<JavaType> parameterTypes) {
        if (getSignature().isEmpty()) {
            return true;
        }
        if ("()".equals(getSignature()) && !parameterTypes.isEmpty()) {
            return false;
        }
        return true;
    }

    default MethodMatcherKey asMethodMatcherKey() {
        final String signature;
        if (this.getSignature().isEmpty()) {
            signature = "(..)";
        } else {
            signature = getSignature();
        }
        final String fullSignature;
        if (this.isConstructor()) {
            fullSignature = getNamespace() + '.' + getType() + ' ' + "<constructor>" + signature;
        } else {
            fullSignature = getNamespace() + '.' + getType() + ' ' + getName() + signature;
        }
        return new MethodMatcherKey(
                fullSignature,
                isSubtypes()
        );
    }

    default Optional<ArgumentRange> getArgumentRange() {
        return computeArgumentRange(getArguments());
    }

    static Optional<ArgumentRange> computeArgumentRange(String arguments) {
        Matcher argumentMatcher = Internal.ARGUMENT_MATCHER.matcher(arguments);

        if (argumentMatcher.matches()) {
            int argumentIndexStart = Integer.parseInt(argumentMatcher.group(1));
            // argumentMatcher.group(2) is null for Argument[x] since ARGUMENT_MATCHER matches Argument[x] and
            // Argument[x..y], and so the null check below ensures that no exception is thrown when Argument[x]
            // is matched
            if (argumentMatcher.group(2) != null) {
                int argumentIndexEnd = Integer.parseInt(argumentMatcher.group(2));
                return Optional.of(new ArgumentRange(argumentIndexStart, argumentIndexEnd));
            } else {
                return Optional.of(new ArgumentRange(argumentIndexStart, argumentIndexStart));
            }
        }
        return Optional.empty();
    }

    @Data
    class MethodMatcherKey {
        final String signature;
        final boolean matchOverrides;
    }

    @Data
    class ArgumentRange {
        // Inclusive
        @Getter
        final int start;
        // Inclusive
        @Getter
        final int end;
    }
}

class Internal {
    static final Pattern ARGUMENT_MATCHER = Pattern.compile("Argument\\[(-?\\d+)\\.?\\.?(\\d+)?]");
}
