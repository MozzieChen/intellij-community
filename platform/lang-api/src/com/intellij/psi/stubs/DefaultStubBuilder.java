/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class DefaultStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.DefaultStubBuilder");

  public StubElement buildStubTree(final PsiFile file) {
    return buildStubTreeFor(file, createStubForFile(file));
  }

  protected StubElement createStubForFile(final PsiFile file) {
    return new PsiFileStubImpl(file);
  }

  // Beware many recursive invokations of this method, the code below reuse locals and parameters to avoid SOE
  protected StubElement buildStubTreeFor(PsiElement elt, StubElement parentStub) {
    ASTNode node;
    IElementType eltType;
    if (elt instanceof StubBasedPsiElement) {
      eltType = ((StubBasedPsiElement)elt).getElementType();

      if (((IStubElementType)eltType).shouldCreateStub(elt.getNode())) {
        //noinspection unchecked
        parentStub = ((IStubElementType)eltType).createStub(elt, parentStub);
      }
    }
    else {
      node = elt.getNode();
      eltType = node == null? null : node.getElementType();
      if (eltType instanceof IStubElementType && ((IStubElementType)eltType).shouldCreateStub(node)) {
        LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + eltType + ", PSI: " + elt);
      }
    }

    for (elt = elt.getFirstChild(); elt != null; elt = elt.getNextSibling()) {
      node = elt.getNode();
      if (!skipChildProcessingWhenBuildingStubs(eltType, node != null ? node.getElementType():null)) {
        buildStubTreeFor(elt, parentStub);
      }
    }

    return parentStub;
  }

  protected StubElement buildStubTreeFor(ASTNode node, StubElement parentStub) {
    StubElement stub = parentStub;
    IElementType nodeType = node.getElementType();

    if (nodeType instanceof IStubElementType) {
      final IStubElementType type = (IStubElementType)nodeType;

      if (type.shouldCreateStub(node)) {
        //noinspection unchecked
        PsiElement element = node.getPsi();
        if (!(element instanceof StubBasedPsiElement)) {
          LOG.error("Non-StubBasedPsiElement requests stub creation. Stub type: " + type + ", PSI: " + element);
        }
        stub = type.createStub(element, parentStub);
      }
    }

    for (ASTNode childNode = node.getFirstChildNode(); childNode != null; childNode = childNode.getTreeNext()) {
      if (!skipChildProcessingWhenBuildingStubs(nodeType, childNode.getElementType())) {
        buildStubTreeFor(childNode, stub);
      }
    }

    return stub;
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(IElementType nodeType, IElementType childType) {
    return false;
  }
}
