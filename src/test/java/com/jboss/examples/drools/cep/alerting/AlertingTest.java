package com.jboss.examples.drools.cep.alerting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.drools.ClockType;
import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.logger.KnowledgeRuntimeLogger;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.runtime.KnowledgeSessionConfiguration;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.conf.ClockTypeOption;
import org.drools.time.SessionPseudoClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jboss.examples.drools.cep.alerting.model.AlertStatus;
import com.jboss.examples.drools.cep.alerting.model.CompoundAlert;
import com.jboss.examples.drools.cep.alerting.model.RawAlert;
import com.jboss.examples.drools.cep.alerting.services.LoggerService;
import com.jboss.examples.drools.cep.alerting.services.impl.MockAlertingMessageService;
import com.jboss.examples.drools.cep.alerting.services.impl.MockDataService;

public class AlertingTest extends BaseAlertingTest {
	
	StatefulKnowledgeSession knowledgeSession;

	@Before
	public void setUp() throws Exception {
		drlFiles = new String[] { "AlertingRules.drl" };
		knowledgeSession = createKnowledgeSessionWithClock();
	}

	@After
	public void tearDown() throws Exception {
		knowledgeSession = null;
	}

	@Test
	public void testTwoCorrelatingAlerts() throws Exception {

		KnowledgeRuntimeLogger logger = null;

		// create the KnowledgeBase
		KnowledgeBase kbase = readKnowledgeBase();
		KnowledgeSessionConfiguration conf = KnowledgeBaseFactory
				.newKnowledgeSessionConfiguration();
		conf.setOption(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.getId()));

		SessionPseudoClock clock = knowledgeSession.getSessionClock();

		// set global variables
		knowledgeSession.setGlobal("dataSvc", new MockDataService());
		knowledgeSession.setGlobal("logger", new LoggerService(clock));
		
		// set a mock service that we can test after fireAllRules()
		MockAlertingMessageService mockJMSSvc = new MockAlertingMessageService(); 
		knowledgeSession.setGlobal("jmsSvc", mockJMSSvc);
		
		// setup the logger
		logger = KnowledgeRuntimeLoggerFactory.newFileLogger(knowledgeSession, "test");

		// cteate an AlertGroup object to store our results
		CompoundAlert alertGroup = new CompoundAlert("test");
		// insert the AlertGroup into the KnowledgeSession
		knowledgeSession.insert(alertGroup);

		long currentTime = new Date().getTime();
		RawAlert[] sa = new RawAlert[] {
				new RawAlert(1, new Date(currentTime), "123", AlertStatus.ACTIVE),
				new RawAlert(2, new Date(currentTime + 10000), "124", AlertStatus.ACTIVE)
		};

		
		for (int i = 0; i < sa.length; i++) {
			clock.advanceTime(
					sa[i].getTime().getTime() - clock.getCurrentTime(),
					TimeUnit.MILLISECONDS);
			knowledgeSession.insert(sa[i]);
			knowledgeSession.fireAllRules();
		}
		
		clock.advanceTime(200, TimeUnit.SECONDS);
		knowledgeSession.fireAllRules();

		assertEquals("The AlertMessagingService should have received a single CompoundAlert.", 1, mockJMSSvc.getAlertGroups().size());
		assertEquals("The CompoundAlert should have 2 Alert objects.", 2, (mockJMSSvc.getAlertGroups().get(0)).numberOfAlerts());
	}

	@Test
	public void testAlertingRules() {
		KnowledgeRuntimeLogger logger = null;
		// set a mock service that we can test after fireAllRules()
		MockAlertingMessageService mockJMSSvc = new MockAlertingMessageService(); 

		try {
			// load up the knowledge base
			KnowledgeBase kbase = readKnowledgeBase();
			KnowledgeSessionConfiguration conf = KnowledgeBaseFactory
					.newKnowledgeSessionConfiguration();
			conf.setOption(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.getId()));
			final StatefulKnowledgeSession ksession = kbase
					.newStatefulKnowledgeSession(conf, null);
			SessionPseudoClock clock = ksession.getSessionClock();

			// set global variables
			ksession.setGlobal("dataSvc", new MockDataService());
			
			ksession.setGlobal("logger", new LoggerService(clock));
			logger = KnowledgeRuntimeLoggerFactory.newFileLogger(ksession,
					"test");

			// set a mock service that we can test after fireAllRules()
			ksession.setGlobal("jmsSvc", mockJMSSvc);

			// go !
			long currentTime = new Date().getTime();
			clock.advanceTime(currentTime, TimeUnit.MILLISECONDS); // set clock
																	// to
																	// current
																	// timestamp

			RawAlert[] sa = new RawAlert[] {
					new RawAlert(1, new Date(currentTime + 10000),
							"123", AlertStatus.ACTIVE),
					new RawAlert(2, new Date(currentTime + 50000),
							"124", AlertStatus.ACTIVE),
					new RawAlert(3, new Date(currentTime + 90000),
							"125", AlertStatus.ACTIVE),
					new RawAlert(4, new Date(currentTime + 150000),
							"126", AlertStatus.ACTIVE),
					new RawAlert(5, new Date(currentTime + 170000),
							"127", AlertStatus.ACTIVE) };

			for (int i = 0; i < sa.length; i++) {
				clock.advanceTime(
						sa[i].getTime().getTime() - clock.getCurrentTime(),
						TimeUnit.MILLISECONDS);
				ksession.insert(sa[i]);
				ksession.fireAllRules();
			}

			clock.advanceTime(100, TimeUnit.SECONDS);
			ksession.fireAllRules();
			
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			if (logger != null)
				logger.close();
		}

		assertNotNull("The MockJMSService should have an ArrayList<AlertGroup>.", mockJMSSvc.getAlertGroups());
		assertEquals("There should be two AlertGroups in the mockJMSSvc.", 2, mockJMSSvc.getAlertGroups().size());
		assertEquals("The AlertGroup should have 2 Alert objects.", 2, (mockJMSSvc.getAlertGroups().get(0)).numberOfAlerts());
	}
}
