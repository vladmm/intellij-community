/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author vlan
 */
public class Py3TypeTest extends PyTestCase {
  public static final String TEST_DIRECTORY = "/types/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  // PY-6702
  public void testYieldFromType() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        doTest("Union[str, int, float]",
               "def subgen():\n" +
               "    for i in [1, 2, 3]:\n" +
               "        yield i\n" +
               "\n" +
               "def gen():\n" +
               "    yield 'foo'\n" +
               "    yield from subgen()\n" +
               "    yield 3.14\n" +
               "\n" +
               "for expr in gen():\n" +
               "    pass\n");
      }
    });
  }

  public void testAwaitAwaitable() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest("int",
               "class C:\n" +
               "    def __await__(self):\n" +
               "        yield 'foo'\n" +
               "        return 0\n" +
               "\n" +
               "async def foo():\n" +
               "    c = C()\n" +
               "    expr = await c\n");
      }
    });
  }

  public void testAsyncDefReturnType() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest("__coroutine[int]",
               "async def foo(x):\n" +
               "    await x\n" +
               "    return 0\n" +
               "\n" +
               "def bar(y):\n" +
               "    expr = foo(y)\n");
      }
    });
  }

  public void testAwaitCoroutine() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest("int",
               "async def foo(x):\n" +
               "    await x\n" +
               "    return 0\n" +
               "\n" +
               "async def bar(y):\n" +
               "    expr = await foo(y)\n");
      }
    });
  }

  // Not in PEP 484 as for now, see https://github.com/ambv/typehinting/issues/119
  public void testCoroutineReturnTypeAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest("int",
               "async def foo() -> int: ...\n" +
               "\n" +
               "async def bar():\n" +
               "    expr = await foo()\n");
      }
    });
  }
  
  // PY-16987
  public void testNoTypeInGoogleDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTest("int", "def f(x: int):\n" +
                      "    \"\"\"\n" +
                      "    Args:\n" +
                      "        x: foo\n" +
                      "    \"\"\"    \n" +
                      "    expr = x");
      }
    });
  }
  
  // PY-16987
  public void testUnfilledTypeInGoogleDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTest("Any", "def f(x: int):\n" +
                      "    \"\"\"\n" +
                      "    Args:\n" +
                      "        x (): foo\n" +
                      "    \"\"\"    \n" +
                      "    expr = x");
      }
    });
  }
  
  // TODO: Same test for Numpy docstrings doesn't pass because typing provider is invoked earlier than NumpyDocStringTypeProvider
  
  // PY-16987
  public void testNoTypeInNumpyDocstringParamAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, new Runnable() {
      @Override
      public void run() {
        doTest("int", "def f(x: int):\n" +
                      "    \"\"\"\n" +
                      "    Parameters\n" +
                      "    ----------\n" +
                      "    x\n" +
                      "        foo\n" +
                      "    \"\"\"    \n" +
                      "    expr = x");
      }
    });
  }
  
  

  private void doTest(final String expectedType, final String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final TypeEvalContext context = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    final PyType actual = context.getType(expr);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals(expectedType, actualType);
  }
}
