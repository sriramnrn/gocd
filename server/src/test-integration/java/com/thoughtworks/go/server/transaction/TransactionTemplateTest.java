/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.server.transaction;

import com.thoughtworks.go.util.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class TransactionTemplateTest {
    @Autowired private TransactionTemplate goTransactionTemplate;
    @Autowired private TransactionSynchronizationManager transactionSynchronizationManager;

    private boolean txnCommitted;
    private boolean txnCompleted;

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;//set in setup

    @BeforeEach
    public void setUp() {
        txnCommitted = false;
        txnCompleted = false;
        transactionTemplate = ReflectionUtil.getField(goTransactionTemplate, "transactionTemplate");
    }

    @Test
    public void shouldBubbleRuntimeExceptionToActualTransactionTemplateForCallback() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);
        try {
            template.executeWithExceptionHandling(new TransactionCallback() {
                @Override public Object doInTransaction(TransactionStatus status) throws Exception {
                    registerSynchronization();
                    throw new Exception("foo");
                }
            });
            fail("should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("foo");
        }
        assertThat(txnCommitted).isFalse();
        assertThat(txnCompleted).isTrue();
    }

    @Test
    public void shouldBubbleRuntimeExceptionToActualTransactionTemplateForCallbackWithoutReturnValue() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);
        try {
            template.executeWithExceptionHandling(new TransactionCallbackWithoutResult() {
                @Override public void doInTransactionWithoutResult(TransactionStatus status) throws Exception {
                    registerSynchronization();
                    throw new Exception("foo");
                }
            });
            fail("should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("foo");
        }
        assertThat(txnCommitted).isFalse();
        assertThat(txnCompleted).isTrue();
    }

    @Test
    public void shouldReturnValueReturnedByCallback() throws Exception {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        String returnVal = (String) template.executeWithExceptionHandling(new TransactionCallback() {
            @Override public Object doInTransaction(TransactionStatus status) {
                registerSynchronization();
                return "foo";
            }
        });
        assertThat(txnCommitted).isTrue();
        assertThat(txnCompleted).isTrue();
        assertThat(returnVal).isEqualTo("foo");
    }

    @Test
    public void shouldReturnNullForCallbackWithoutReturn() throws Exception {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        Object returnVal = template.executeWithExceptionHandling(new TransactionCallbackWithoutResult() {
            @Override public void doInTransactionWithoutResult(TransactionStatus status) {
                registerSynchronization();
            }
        });
        assertThat(txnCommitted).isTrue();
        assertThat(txnCompleted).isTrue();
        assertThat(returnVal).isNull();
    }

    @Test
    public void shouldExecuteTransactionCallback() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        String returnVal = template.execute(status -> {
            registerSynchronization();
            return "foo";
        });
        assertThat(txnCommitted).isTrue();
        assertThat(txnCompleted).isTrue();
        assertThat(returnVal).isEqualTo("foo");
    }

    @Test
    public void shouldUnderstand_InTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] inTransactionInBody = new boolean[] {false};
        final boolean[] inTransactionInAfterCommit = new boolean[] {true};
        final boolean[] inTransactionInAfterComplete = new boolean[] {true};

        String returnVal = template.execute(status -> {
            setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 0);
            return "foo";
        });

        assertThat(inTransactionInBody[0]).isTrue();
        assertThat(inTransactionInAfterCommit[0]).isFalse();
        assertThat(inTransactionInAfterComplete[0]).isFalse();
        assertThat(returnVal).isEqualTo("foo");
    }

    @Test
    public void shouldAllowRegistrationOfTransactionSynchronization_inTransactionSurroundingBlock_andExecuteAppropriateHooks() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] afterCommitHappened = new boolean[1];
        final boolean[] transactionWasActiveInSurrounding = new boolean[1];
        final boolean[] transactionWasActiveInTransaction = new boolean[1];

        String returnVal = (String) template.transactionSurrounding(() -> {
            transactionWasActiveInSurrounding[0] = transactionSynchronizationManager.isTransactionBodyExecuting();

            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    afterCommitHappened[0] = true;
                }
            });
            return template.execute(status -> {
                transactionWasActiveInTransaction[0] = transactionSynchronizationManager.isTransactionBodyExecuting();
                return "foo";
            });
        });

        assertThat(returnVal).isEqualTo("foo");
        assertThat(afterCommitHappened[0]).isTrue();
        assertThat(transactionWasActiveInSurrounding[0]).isFalse();
        assertThat(transactionWasActiveInTransaction[0]).isTrue();
    }

    @Test
    public void shouldAllowRegistrationOfTransactionSynchronization_inTransactionSurroundingBlock_andNotExecuteSynchronizationIfTransactionNeverHappens() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] afterCommitHappened = new boolean[1];

        String returnVal = (String) template.transactionSurrounding(() -> {
            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    afterCommitHappened[0] = true;
                }
            });
            return "bar";
        });

        assertThat(returnVal).isEqualTo("bar");
        assertThat(afterCommitHappened[0]).isFalse();
    }

    @Test
    public void should_NOT_useSynchronizationsFromOneSurroundingBlockInAnother() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] afterCommitHappened = new boolean[1];

        String returnVal = (String) template.transactionSurrounding(() -> {

            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    afterCommitHappened[0] = true;
                }
            });
            return "foo";
        });

        assertThat(returnVal).isEqualTo("foo");
        assertThat(afterCommitHappened[0]).isFalse();//because no transaction happened

        returnVal = (String) template.transactionSurrounding(() -> template.execute(status -> "bar"));

        assertThat(returnVal).isEqualTo("bar");
        assertThat(afterCommitHappened[0]).isFalse();//because it registered no synchronization
    }

    @Test
    public void shouldPropagateExceptionsOutOfTransactionSurrounding() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] afterCommitHappened = new boolean[1];

        String returnVal = null;
        try {
            returnVal = (String) template.transactionSurrounding(() -> {
                transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                    @Override public void afterCommit() {
                        afterCommitHappened[0] = true;
                    }
                });
                throw new IOException("boo ha!");
            });
            fail("should have propagated exception");
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("boo ha!");
        }

        assertThat(returnVal).isNull();
        assertThat(afterCommitHappened[0]).isFalse();
    }

    @Test
    public void shouldNotRegisterSynchronizationMultipleTimes() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final int[] numberOfTimesAfterCommitHappened = new int[1];

        String returnVal = (String) template.transactionSurrounding(() -> {
            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    numberOfTimesAfterCommitHappened[0]++;
                }
            });
            return template.execute(status -> goTransactionTemplate.execute(status1 -> "foo"));
        });

        assertThat(returnVal).isEqualTo("foo");
        assertThat(numberOfTimesAfterCommitHappened[0]).isEqualTo(1);
    }

    @Test
    public void should_NOT_AllowMoreThanOneTransactionInsideSurrounding() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        String returnVal = null;
        try {
            returnVal = (String) template.transactionSurrounding(() -> {
                template.execute(status -> "foo");

                return template.execute(status -> "bar");
            });
            fail("should not have allowed multiple top-level transactions");//this can cause assumptions of registered-synchronization to become invalid -jj
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Multiple independent transactions are not permitted inside single transaction surrounding.");
        }

        assertThat(returnVal).isNull();
    }

    @Test
    public void shouldAllowMoreThanOneTransactionInsideSurrounding_ifSurroundingIsInsideTransactionAlready() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] firstNestedTransactionHappened = new boolean[1];
        final boolean[] secondNestedTransactionHappened = new boolean[1];

        final boolean[] firstNestedTransactionCalledTransactionSynchronization = new boolean[1];
        final boolean[] secondNestedTransactionCalledTransactionSynchronization = new boolean[1];

        final int[] numberOfTimesSynchronizationWasCalled = new int[1];

        String returnVal = (String) template.execute(status -> template.transactionSurrounding(() -> {
            transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    numberOfTimesSynchronizationWasCalled[0]++;
                }
            });

            template.execute(status12 -> {
                firstNestedTransactionHappened[0] = true;
                return "foo";
            });

            firstNestedTransactionCalledTransactionSynchronization[0] = numberOfTimesSynchronizationWasCalled[0] > 0;

            Object ret = template.execute(status1 -> {
                secondNestedTransactionHappened[0] = true;
                return "bar";
            });

            secondNestedTransactionCalledTransactionSynchronization[0] = numberOfTimesSynchronizationWasCalled[0] > 0;

            return ret;
        }));

        assertThat(returnVal).isEqualTo("bar");
        assertThat(numberOfTimesSynchronizationWasCalled[0]).isEqualTo(1);

        assertThat(firstNestedTransactionHappened[0]).isTrue();
        assertThat(firstNestedTransactionCalledTransactionSynchronization[0]).isFalse();

        assertThat(secondNestedTransactionHappened[0]).isTrue();
        assertThat(secondNestedTransactionCalledTransactionSynchronization[0]).isFalse();
    }

    @Test
    public void shouldUnderstand_InTransaction_AcrossNestedInvocations() {
        final TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] inTransactionInBody = new boolean[] {false, false, false, false};
        final boolean[] inTransactionInAfterCommit = new boolean[] {true, true, true, true};
        final boolean[] inTransactionInAfterComplete = new boolean[] {true, true, true, true};

        String returnVal = (String) template.execute(status -> {
            setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 0);
            return template.execute(status12 -> {
                setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 1);
                try {
                    return template.executeWithExceptionHandling(new TransactionCallback() {
                        @Override
                        public Object doInTransaction(TransactionStatus status12) {
                            setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 2);
                            return template.execute(status1 -> {
                                setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 3);
                                return "baz";
                            });
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });

        for (int i = 0; i < 4; i++) {
            assertThat(inTransactionInBody[i]).isTrue();
            assertThat(inTransactionInAfterCommit[i]).isFalse();
            assertThat(inTransactionInAfterComplete[i]).isFalse();
        }
        assertThat(returnVal).isEqualTo("baz");
    }

    private void setTxnBodyActiveFlag(final boolean[] inTransactionInBody, final boolean[] inTransactionInAfterCommit, final boolean[] inTransactionInAfterComplete, final int depth) {
        inTransactionInBody[depth] = transactionSynchronizationManager.isTransactionBodyExecuting();

        transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override public void afterCommit() {
                inTransactionInAfterCommit[depth] = transactionSynchronizationManager.isTransactionBodyExecuting();
            }

            @Override public void afterCompletion(int status) {
                inTransactionInAfterComplete[depth] = transactionSynchronizationManager.isTransactionBodyExecuting();
            }
        });
    }

    @Test
    public void shouldUnderstand_InTransaction_ForTransactionWithExceptionHandling() throws Exception {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate);

        final boolean[] inTransactionInBody = new boolean[] {false};
        final boolean[] inTransactionInAfterCommit = new boolean[] {true};
        final boolean[] inTransactionInAfterComplete = new boolean[] {true};

        String returnVal = (String) template.executeWithExceptionHandling(new TransactionCallback() {
            @Override public Object doInTransaction(TransactionStatus status) {
                setTxnBodyActiveFlag(inTransactionInBody, inTransactionInAfterCommit, inTransactionInAfterComplete, 0);
                return "foo";
            }
        });

        assertThat(inTransactionInBody[0]).isTrue();
        assertThat(inTransactionInAfterCommit[0]).isFalse();
        assertThat(inTransactionInAfterComplete[0]).isFalse();
        assertThat(returnVal).isEqualTo("foo");
    }

    @Test
    public void shouldNotFailWhenNoTransactionStarted() throws InterruptedException {
        final boolean[] transactionBodyIn = new boolean[] {true};

        Thread thd = new Thread(() -> transactionBodyIn[0] = TransactionTemplate.isTransactionBodyExecuting());
        thd.start();
        thd.join();

        assertThat(transactionBodyIn[0]).isFalse();
    }

    private void registerSynchronization() {
        transactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override public void afterCommit() {
                txnCommitted = true;
            }

            @Override public void afterCompletion(int status) {
                txnCompleted = true;
            }
        });
    }
}
