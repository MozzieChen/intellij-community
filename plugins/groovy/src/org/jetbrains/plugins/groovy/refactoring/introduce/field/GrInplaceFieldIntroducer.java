/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;

/**
 * @author Max Medvedev
 */
public class GrInplaceFieldIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceFieldSettings> {
  private GrInplaceIntroduceFieldPanel myPanel;
  private final GrFinalListener finalListener;
  private String[] mySuggestedNames;
  private boolean myIsStatic;

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return ((PsiField)getVariable()).getContainingClass();
  }

  public GrInplaceFieldIntroducer(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(IntroduceFieldHandler.REFACTORING_NAME, choice, context);

    finalListener = new GrFinalListener(myEditor);

    mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(context.getExpression(), new GroovyInplaceFieldValidator(getContext()), false);
  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceFieldSettings settings, boolean processUsages) {
      GrIntroduceFieldProcessor processor = new GrIntroduceFieldProcessor(context, settings);
      return processUsages ? processor.run()
                           : processor.insertField((PsiClass)context.getScope()).getVariables()[0];
  }

  @Nullable
  @Override
  protected GrIntroduceFieldSettings getInitialSettingsForInplace(@NotNull final GrIntroduceContext context,
                                                                  @NotNull final OccurrencesChooser.ReplaceChoice choice,
                                                                  final String[] names) {
    return new GrIntroduceFieldSettings() {
      @Override
      public boolean declareFinal() {
        return false;
      }

      @Override
      public Init initializeIn() {
        return Init.FIELD_DECLARATION;
      }

      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PRIVATE;
      }

      @Override
      public boolean isStatic() {
        boolean hasInstanceInScope = true;
        PsiClass clazz = (PsiClass)context.getScope();
        if (replaceAllOccurrences()) {
          for (PsiElement occurrence : context.getOccurrences()) {
            if (!PsiUtil.hasEnclosingInstanceInScope(clazz, occurrence, false)) {
              hasInstanceInScope = false;
              break;
            }
          }
        }
        else if (context.getExpression() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getExpression(), false);
        }
        else if (context.getStringPart() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getStringPart().getLiteral(), false);
        }

        return !hasInstanceInScope;
      }

      @Override
      public boolean removeLocalVar() {
        return context.getVar() != null;
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return context.getVar() != null || choice == OccurrencesChooser.ReplaceChoice.ALL;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        GrExpression expression = context.getExpression();
        GrVariable var = context.getVar();
        StringPartInfo stringPart = context.getStringPart();
        return var != null ? var.getDeclaredType() :
               expression != null ? expression.getType() :
               stringPart != null ? stringPart.getLiteral().getType() :
               null;
      }
    };
  }

  @Override
  protected GrIntroduceFieldSettings getSettings() {
    return new GrIntroduceFieldSettings() {
      @Override
      public boolean declareFinal() {
        return myPanel.isFinal();
      }

      @Override
      public Init initializeIn() {
        return myPanel.getInitPlace();
      }

      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PRIVATE;
      }

      @Override
      public boolean isStatic() {
        return myIsStatic;
      }

      @Override
      public boolean removeLocalVar() {
        return false;
      }

      @Nullable
      @Override
      public String getName() {
        return getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return GrInplaceFieldIntroducer.this.getSelectedType();
      }
    };
  }

  @Override
  protected String getActionName() {
    return IntroduceFieldHandler.REFACTORING_NAME;
  }

  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return mySuggestedNames;
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected void restoreState(GrVariable psiField) {
    myIsStatic = psiField.hasModifierProperty(PsiModifier.STATIC);

    super.restoreState(psiField);
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    myPanel = new GrInplaceIntroduceFieldPanel(myProject, GrIntroduceFieldHandler.getApplicableInitPlaces(getContext(), isReplaceAllOccurrences()));
    return myPanel.getRootPane();
  }

  public class GrInplaceIntroduceFieldPanel {
    private final Project myProject;
    private JPanel myRootPane;
    private JComboBox myInitCB;
    private NonFocusableCheckBox myDeclareFinalCB;
    private JComponent myPreview;

    public GrInplaceIntroduceFieldPanel(Project project, EnumSet<GrIntroduceFieldSettings.Init> initPlaces) {
      myProject = project;

      KeyboardComboSwitcher.setupActions(myInitCB, project);

      for (GrIntroduceFieldSettings.Init place : initPlaces) {
        myInitCB.addItem(place);
      }

      myDeclareFinalCB.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
            @Override
            protected void run(Result result) throws Throwable {
              PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
              final GrVariable variable = getVariable();
              if (variable != null) {
                finalListener.perform(myDeclareFinalCB.isSelected(), variable);
              }
            }
          }.execute();
        }
      });
    }

    public JPanel getRootPane() {
      return myRootPane;
    }

    public GrIntroduceFieldSettings.Init getInitPlace() {
      return (GrIntroduceFieldSettings.Init)myInitCB.getSelectedItem();
    }

    public boolean isFinal() {
      return myDeclareFinalCB.isSelected();
    }

    private void createUIComponents() {
      myPreview = getPreviewComponent();
    }
  }
}
