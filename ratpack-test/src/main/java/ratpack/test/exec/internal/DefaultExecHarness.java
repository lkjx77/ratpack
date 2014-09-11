/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.test.exec.internal;

import ratpack.exec.ExecControl;
import ratpack.exec.ExecController;
import ratpack.exec.Execution;
import ratpack.exec.Promise;
import ratpack.exec.internal.DefaultResult;
import ratpack.func.Action;
import ratpack.func.Function;
import ratpack.test.exec.ExecHarness;
import ratpack.test.exec.ExecResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultExecHarness implements ExecHarness {

  private final ExecController controller;

  public DefaultExecHarness(ExecController controller) {
    this.controller = controller;
  }

  @Override
  public <T> ExecResult<T> execute(final Function<Execution, Promise<T>> func) throws Exception {
    final AtomicReference<ExecResult<T>> reference = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    final Action<Throwable> onError = new Action<Throwable>() {
      @Override
      public void execute(Throwable throwable) throws Exception {
        reference.set(new ResultBackedExecResult<>(DefaultResult.<T>failure(throwable)));
        latch.countDown();
      }
    };

    controller.getControl().fork(new Action<Execution>() {
      @Override
      public void execute(Execution execution) throws Exception {
        execution.onCleanup(new AutoCloseable() {
          @Override
          public void close() {
            if (latch.getCount() > 0) {
              reference.set(new CompleteExecResult<T>());
              latch.countDown();
            }
          }
        });
        Promise<T> promise = func.apply(execution);

        if (promise == null) {
          succeed(null);
        } else {
          promise.
            then(new Action<T>() {
              @Override
              public void execute(T t) throws Exception {
                succeed(t);
              }
            });
        }
      }

      private void succeed(T t) {
        reference.set(t == null ? null : new ResultBackedExecResult<>(DefaultResult.success(t)));
        latch.countDown();
      }
    }, onError);

    latch.await();
    return reference.get();
  }

  @Override
  public ExecControl getControl() {
    return controller.getControl();
  }

  @Override
  public void close() {
    controller.close();
  }

}
