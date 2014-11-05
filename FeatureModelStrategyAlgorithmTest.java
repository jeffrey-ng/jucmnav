package seg.jUCMNav.tests;

import junit.framework.TestCase;
import static org.junit.Assert.*;
import fm.FeatureModel;
import grl.EvaluationStrategy;
import grl.GrlFactory;
import grl.impl.EvaluationStrategyImpl;
import grl.impl.GRLspecImpl;
import grl.impl.GrlFactoryImpl;
import grl.GRLspec;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert.*;

import seg.jUCMNav.extensionpoints.IGRLStrategyAlgorithm;
import seg.jUCMNav.strategies.FeatureModelStrategyAlgorithm;

public class FeatureModelStrategyAlgorithmTest extends TestCase{
	EvaluationStrategy strategy;
	FeatureModelStrategyAlgorithm algo;
	
	public FeatureModelStrategyAlgorithmTest() {
		// TODO Auto-generated constructor stub
	}
	
	@Before
	protected void setUp() throws Exception {
		// TODO Auto-generated method stub
		super.setUp();
		GrlFactory factory = GrlFactoryImpl.init();
		strategy = factory.createEvaluationStrategy();
		GRLspec spec = factory.createGRLspec();
		strategy.setGrlspec(spec);
		algo = new FeatureModelStrategyAlgorithm();		
		
	}
	
	@After
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		algo = null;
		strategy = null;
	}
	
	@Test
	public void testGetEvaluationType() {
		int v = algo.getEvaluationType();
		assertEquals(v,IGRLStrategyAlgorithm.EVAL_FEATURE_MODEL);
	}
}
