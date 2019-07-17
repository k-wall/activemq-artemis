/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.compatibility;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.HORNETQ_235;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ONE_FIVE;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;

/**
 * To run this test on the IDE and debug it, run the compatibility-tests through a command line once:
 *
 * cd /compatibility-tests
 * mvn install -Ptests | tee output.log
 *
 * on the output.log you will see the output generated by {@link #getClasspath(String)}
 *
 * On your IDE, edit the Run Configuration to your test and add those -D as parameters to your test.
 * On Idea you would do the following:
 *
 * Run->Edit Configuration->Add ArtemisMeshTest and add your properties.
 */
public class HornetQSoakTest extends ClasspathBase {

   ClassLoader artemisClassLoader;
   ClassLoader artemis1XClassLoader;
   ClassLoader hornetqClassLoader;

   @Before
   public void setUp() throws Throwable {

      this.artemisClassLoader = getClasspath(SNAPSHOT);
      this.artemis1XClassLoader = getClasspath(ONE_FIVE);
      this.hornetqClassLoader = getClasspath(HORNETQ_235);

      FileUtil.deleteDirectory(serverFolder.getRoot());
      setVariable(artemisClassLoader, "persistent", Boolean.FALSE);
      startServer(serverFolder.getRoot(), artemisClassLoader, "live", null, true, "hqsoak/artemisServer.groovy", SNAPSHOT, SNAPSHOT, SNAPSHOT);
   }

   @After
   public void tearDown() throws Throwable {
      if (artemisClassLoader != null && hornetqClassLoader != null) {
         stopServer(artemisClassLoader);
      }
   }

   @Test
   public void testSoakHornetQ() throws Throwable {
      ReusableLatch reusableLatch = new ReusableLatch(0);

      int threadsProducerArtemis = 2;
      int numberOfMessagesArtemis = 5;
      int threadsProducerHornetQ = 2;
      int numberOfMessagesHornetQ = 5;

      int numberOfConsumersArtemis = 10;
      int numberOfConsumersArtemis1X = 5;
      int numberOfConsumersHornetQ = 10;

      int multiplyFactor = numberOfConsumersArtemis + numberOfConsumersArtemis1X + numberOfConsumersHornetQ;

      setVariable(artemisClassLoader, "reusableLatch", reusableLatch);
      setVariable(artemisClassLoader, "multiplyFactor", multiplyFactor);
      setVariable(artemis1XClassLoader, "reusableLatch", reusableLatch);
      setVariable(artemis1XClassLoader, "multiplyFactor", multiplyFactor);
      setVariable(hornetqClassLoader, "reusableLatch", reusableLatch);
      setVariable(hornetqClassLoader, "multiplyFactor", multiplyFactor);

      int totalMessagePerQueue = threadsProducerArtemis * numberOfMessagesArtemis + threadsProducerHornetQ * numberOfMessagesHornetQ;

      AtomicInteger runningConsumerArtemis = (AtomicInteger) evaluate(artemisClassLoader, "hqsoak/receiveMessages.groovy", "ARTEMIS", "" + numberOfConsumersArtemis, "" + totalMessagePerQueue);
      AtomicInteger runningConsumer1XArtemis = (AtomicInteger) evaluate(artemis1XClassLoader, "hqsoak/receiveMessages.groovy", "ARTEMIS1X", "" + numberOfConsumersArtemis, "" + totalMessagePerQueue);
      AtomicInteger runningConsumerHornetQ = (AtomicInteger) evaluate(hornetqClassLoader, "hqsoak/receiveMessages.groovy", "HORNETQ", "" + numberOfConsumersHornetQ, "" + totalMessagePerQueue);

      System.out.println("Running producers");

      AtomicInteger ranArtemisProducer = (AtomicInteger) evaluate(artemisClassLoader, "hqsoak/sendMessages.groovy", "ARTEMIS", "" + threadsProducerArtemis, "" + numberOfMessagesArtemis);
      AtomicInteger ranHornetQProducer = (AtomicInteger) evaluate(hornetqClassLoader, "hqsoak/sendMessages.groovy", "HORNETQ", "" + threadsProducerHornetQ, "" + numberOfMessagesHornetQ);

      Wait.assertEquals(threadsProducerArtemis, ranArtemisProducer::get, TimeUnit.MINUTES.toMillis(5), 100);
      Wait.assertEquals(threadsProducerHornetQ, ranHornetQProducer::get, TimeUnit.MINUTES.toMillis(5), 100);

      Wait.assertEquals(0, runningConsumerArtemis::get);
      Wait.assertEquals(0, runningConsumerHornetQ::get);
      Wait.assertEquals(0, runningConsumer1XArtemis::get);

      checkErrors(artemisClassLoader, "errorsProducer");
      checkErrors(hornetqClassLoader, "errorsProducer");
      checkErrors(artemisClassLoader, "errorsConsumer");
      checkErrors(hornetqClassLoader, "errorsConsumer");
      checkErrors(artemis1XClassLoader, "errorsConsumer");

      // execute(artemisClassLoader, )

   }

   protected void checkErrors(ClassLoader loader, String variable) throws Throwable {
      AtomicInteger errors = (AtomicInteger) execute(loader, "return " + variable);
      Assert.assertEquals("the script finished with errors", 0, errors.get());
   }

}