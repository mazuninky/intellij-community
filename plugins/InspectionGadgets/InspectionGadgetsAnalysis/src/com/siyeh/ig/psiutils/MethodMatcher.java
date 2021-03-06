// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Remember to call readSettings() and writeSettings from your inspection class!
 * @author Bas Leijdekkers
 */
public class MethodMatcher {

  private final List<String> myMethodNamePatterns = new ArrayList<>();
  private final List<String> myClassNames = new ArrayList<>();
  private final Map<String, Pattern> myPatternCache = new HashMap<>();
  private final boolean myWriteDefaults;
  private final String myOptionName;
  private String myDefaultSettings = null;

  public MethodMatcher() {
    this(false, "METHOD_MATCHER_CONFIG");
  }

  public MethodMatcher(boolean writeDefaults, String optionName) {
    myWriteDefaults = writeDefaults;
    myOptionName = optionName;
  }

  public MethodMatcher add(@NotNull String className, @NotNull String methodNamePattern) {
    myClassNames.add(className);
    myMethodNamePatterns.add(methodNamePattern);
    return this;
  }

  public void add(@NotNull PsiMethodCallExpression expression) {
    final PsiMethod method = expression.resolveMethod();
    if (method != null) {
      add(method);
    }
  }

  public void add(@NotNull PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return;
    }
    final String fqName = aClass.getQualifiedName();
    final int index = myClassNames.indexOf(fqName);
    final String methodName = method.getName();
    if (index < 0) {
      myClassNames.add(fqName);
      myMethodNamePatterns.add(methodName);
    }
    else {
      final String pattern = myMethodNamePatterns.get(index);
      if (pattern.isEmpty()) {
        myMethodNamePatterns.set(index, methodName);
        return;
      }
      else if (".*".equals(pattern)) {
        return;
      }
      final String[] names = pattern.split("\\|");
      for (String name : names) {
        if (methodName.equals(name)) {
          return;
        }
      }
      myMethodNamePatterns.set(index, pattern + '|' + methodName);
    }
    ProjectInspectionProfileManager.getInstance(method.getProject()).fireProfileChanged();
  }

  @NotNull
  protected String getOptionName() {
    return myOptionName;
  }

  public List<String> getMethodNamePatterns() {
    return myMethodNamePatterns;
  }

  public List<String> getClassNames() {
    return myClassNames;
  }

  public boolean matches(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final String methodName = method.getName();
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    for (int i = 0, size = myMethodNamePatterns.size(); i < size; i++) {
      final Pattern pattern = getPattern(i);
      if (pattern == null || !pattern.matcher(methodName).matches()) {
        continue;
      }
      final String className = myClassNames.get(i);
      if (InheritanceUtil.isInheritor(aClass, className)) {
        return true;
      }
    }
    return false;
  }

  public boolean matches(PsiCall call) {
    return matches(call.resolveMethod());
  }

  private Pattern getPattern(int i) {
    final String methodNamePattern = myMethodNamePatterns.get(i);
    Pattern pattern = myPatternCache.get(methodNamePattern);
    if (pattern == null) {
      try {
        pattern = Pattern.compile(methodNamePattern);
        myPatternCache.put(methodNamePattern, pattern);
      }
      catch (PatternSyntaxException | NullPointerException ignore) {
        return null;
      }
    }
    return pattern;
  }

  public MethodMatcher finishDefault() {
    if (myDefaultSettings != null) throw new IllegalStateException();
    myDefaultSettings = BaseInspection.formatString(myClassNames, myMethodNamePatterns);
    return this;
  }

  public void readSettings(@NotNull Element node) throws InvalidDataException {
    String settings = null;
    for (Element option : node.getChildren("option")) {
      if (option.getAttributeValue("name").equals(getOptionName())) {
        settings = option.getAttributeValue("value");
        break;
      }
    }
    if (settings == null) return;
    myPatternCache.clear();
    myClassNames.clear();
    myMethodNamePatterns.clear();
    BaseInspection.parseString(settings, myClassNames, myMethodNamePatterns);
  }

  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    final String settings = BaseInspection.formatString(myClassNames, myMethodNamePatterns);
    if (!myWriteDefaults && settings.equals(myDefaultSettings)) return;
    node.addContent(new Element("option").setAttribute("name", getOptionName()).setAttribute("value", settings));
  }
}
