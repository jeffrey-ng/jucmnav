package seg.jUCMNav.tests;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import fm.Feature;
import fm.FeatureDiagram;
import fm.FeatureModel;
import fm.FmFactory;
import fm.MandatoryFMLink;
import fm.OptionalFMLink;
import fm.impl.FeatureModelImpl;
import fm.impl.FeatureModelImpl.EvaluationResult;
import grl.ActorRef;
import grl.Belief;
import grl.ContributionType;
import grl.Decomposition;
import grl.DecompositionType;
import grl.ElementLink;
import grl.Evaluation;
import grl.EvaluationStrategy;
import grl.GRLGraph;
import grl.GRLNode;
import grl.GrlFactory;
import grl.IntentionalElement;
import grl.IntentionalElementRef;
import grl.StrategiesGroup;
import grl.impl.GrlFactoryImpl;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ca.mcgill.sel.core.COREFeature;
import ca.mcgill.sel.core.COREFeatureModel;
import ca.mcgill.sel.core.COREFeatureRelationshipType;
import ca.mcgill.sel.core.COREFeatureSelectionStatus;
import ca.mcgill.sel.core.COREImpactModelElement;
import seg.jUCMNav.core.COREFactory4URN;
import seg.jUCMNav.editors.UCMNavMultiPageEditor;
import seg.jUCMNav.editparts.IntentionalElementEditPart;
import seg.jUCMNav.extensionpoints.IGRLStrategyAlgorithm;
import seg.jUCMNav.model.ModelCreationFactory;
import seg.jUCMNav.model.commands.create.AddIntentionalElementRefCommand;
import seg.jUCMNav.model.commands.create.CreateElementLinkCommand;
import seg.jUCMNav.model.commands.create.CreateFMDCommand;
import seg.jUCMNav.model.commands.create.CreateGrlGraphCommand;
import seg.jUCMNav.model.commands.create.CreateStrategiesGroupCommand;
import seg.jUCMNav.model.commands.create.CreateStrategyCommand;
import seg.jUCMNav.model.commands.delete.DeleteMapCommand;
import seg.jUCMNav.model.commands.transformations.ChangeDecompositionTypeCommand;
import seg.jUCMNav.model.commands.transformations.ChangeGrlNodeNameCommand;
import seg.jUCMNav.model.commands.transformations.ChangeNumericalContributionCommand;
import seg.jUCMNav.model.commands.transformations.ChangeNumericalEvaluationCommand;
import seg.jUCMNav.model.util.ParentFinder;
import seg.jUCMNav.strategies.EvaluationStrategyManager;
import seg.jUCMNav.strategies.FeatureModelStrategyAlgorithm;
import seg.jUCMNav.strategies.util.FeatureUtil;
import seg.jUCMNav.views.preferences.DeletePreferences;
import seg.jUCMNav.views.preferences.StrategyEvaluationPreferences;
import ucm.map.UCMmap;
import urn.URNlink;
import urn.URNspec;
import urncore.IURNDiagram;
import junit.framework.TestCase;

public class FeatureModelStrategyAlgorithmTest extends TestCase {

	enum LinkType {
		MANDATORY, OPTIONAL, XOR, OR, AND
	}

	private UCMNavMultiPageEditor editor;
	private CommandStack cs;

	private URNspec urnspec;
	private URNspec urn;
	private FeatureDiagram fd;
	private FeatureModel fm;
	// private Actor actor;
	private ActorRef actorref;
	private ActorRef actorref2;
	private ElementLink link;
	private StrategiesGroup strategiesgroup;
	private EvaluationStrategy strategy;
	private URNlink urnlink;

	private FeatureModelStrategyAlgorithm algo;

	public FeatureModelStrategyAlgorithmTest() {
		// TODO Auto-generated constructor stub
	}

	@Before
	protected void setUp() throws Exception {
		// TODO Auto-generated method stu
		super.setUp();
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject testProject = workspaceRoot.getProject("jUCMNav-GRL-tests");
		if (!testProject.exists()) {
			testProject.create(null);
		}
		if (!testProject.isOpen()) {
			testProject.open(null);
		}
		IFile testFile = testProject.getFile("jUCMNav-GRL-tests.jucm");

		if (testFile.exists()) {
			testFile.delete(true, false, null);
		}

		testFile.create(new ByteArrayInputStream("".getBytes()), false, null);
		IWorkbenchPage page = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getActivePage();
		IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry()
				.getDefaultEditor(testFile.getName());
		editor = (UCMNavMultiPageEditor) page.openEditor(new FileEditorInput(
				testFile), desc.getId());
		UCMNavMultiPageEditor e = new UCMNavMultiPageEditor();
		// generate a top level model element
		urnspec = editor.getModel();

		cs = editor.getDelegatingCommandStack();

		Command cmd;
		Object defaultMap = urnspec.getUrndef().getSpecDiagrams().get(0);
		if (defaultMap instanceof UCMmap) {
			cmd = new DeleteMapCommand((UCMmap) defaultMap);
			assertTrue("Can't execute DeleteMapCommand.", cmd.canExecute()); //$NON-NLS-1$
			cs.execute(cmd);
		}

		// Create a new GRLGraph
		cmd = new CreateGrlGraphCommand(urnspec);
		assertTrue("Can't execute CreateGrlGraphCommand.", cmd.canExecute()); //$NON-NLS-1$
		cs.execute(cmd);

		// Set the preferences for deleting the references to ALWAYS
		DeletePreferences.getPreferenceStore().setValue(
				DeletePreferences.PREF_DELDEFINITION,
				DeletePreferences.PREF_ALWAYS);
		DeletePreferences.getPreferenceStore().setValue(
				DeletePreferences.PREF_DELREFERENCE,
				DeletePreferences.PREF_ALWAYS);

	}

	@After
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		editor.doSave(null);

		editor.closeEditor(false);
	}

	private Feature setupTest() {
		urn = ModelCreationFactory.getNewURNspec(true, true, true);
		FeatureModel fm = urn.getGrlspec().getFeatureModel();
		FeatureDiagram fd = null;
		Feature root = null;
		Vector<IntentionalElementRef> featureRefs = new Vector<IntentionalElementRef>();

		StrategyEvaluationPreferences.createPreferences();
		StrategyEvaluationPreferences
				.setAlgorithm(StrategyEvaluationPreferences.FEATURE_MODEL_ALGORITHM
						+ "");
		StrategyEvaluationPreferences.setTolerance(0);
		StrategyEvaluationPreferences.getPreferenceStore().setValue(
				"PREF_AUTOSELECTMANDATORYFEATURES", true);

		Iterator it4 = urn.getUrndef().getSpecDiagrams().iterator();
		while (it4.hasNext()) {
			IURNDiagram diagram = (IURNDiagram) it4.next();
			if (diagram instanceof FeatureDiagram)
				fd = (FeatureDiagram) diagram;
		}
		if (root == null) {
			IntentionalElementRef ref = (IntentionalElementRef) ModelCreationFactory
					.getNewObject(urn, IntentionalElementRef.class,
							ModelCreationFactory.FEATURE);
			AddIntentionalElementRefCommand aierCmd = new AddIntentionalElementRefCommand(
					fd, ref);
			aierCmd.execute();
		}
		List<Feature> roots = FeatureUtil.getRootFeatures(fm.getGrlspec());
		if (!roots.isEmpty()) {
			// root feature exists (take the first one as URN does not constrain
			// feature models to one root), but is it placed on a feature
			// diagram?
			root = roots.get(0);
		}
		root.setName("root");
		this.fd = fd;
		this.fm = fm;
		return root;

	}

	private Feature createFeature(IntentionalElement parent, String name,
			LinkType relationship, int numericalContribution) {
		IntentionalElementRef ref = (IntentionalElementRef) ModelCreationFactory
				.getNewObject(urn, IntentionalElementRef.class,
						ModelCreationFactory.FEATURE);
		AddIntentionalElementRefCommand aierCmd = new AddIntentionalElementRefCommand(
				fd, ref);
		aierCmd.execute();
		ChangeGrlNodeNameCommand cgnnCmd = new ChangeGrlNodeNameCommand(ref,
				name);
		cgnnCmd.execute();

		ElementLink link = null;
		int type = 0;
		if (relationship == LinkType.MANDATORY) {
			// add mandatory link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn,
					MandatoryFMLink.class);
		} else if (relationship == LinkType.OPTIONAL) {
			// add optional link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn,
					OptionalFMLink.class);
		} else if (relationship == LinkType.XOR) {
			// add XOR decomposition link between this feature and the new child
			// feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn,
					Decomposition.class);
			type = 2;
		} else if (relationship == LinkType.OR) {
			// add OR decomposition link between this feature and the new child
			// feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn,
					Decomposition.class);
			type = 1;
		} else if (relationship == LinkType.AND) {
			link = (ElementLink) ModelCreationFactory.getNewObject(urn,
					Decomposition.class);
			type = 0;
		}

		CreateElementLinkCommand celCmd = new CreateElementLinkCommand(urn,
				(IntentionalElement) ref.getDef(), link);
		celCmd.setTarget(parent);
		if (celCmd.canExecute())
			celCmd.execute();
		if (relationship == LinkType.MANDATORY
				|| relationship == LinkType.OPTIONAL) {
			ChangeNumericalContributionCommand cncmd3 = new ChangeNumericalContributionCommand(
					link.getRefs(),
					ChangeNumericalEvaluationCommand.USER_ENTRY,
					numericalContribution, cs);
			cncmd3.execute();
		}

		if (relationship == LinkType.XOR || relationship == LinkType.OR
				|| relationship == LinkType.AND) {
			ChangeDecompositionTypeCommand cdtCmd = new ChangeDecompositionTypeCommand(
					(IntentionalElementRef) parent.getRefs().get(0), type);
			if (cdtCmd.canExecute())
				cdtCmd.execute();
		}

		return (Feature) ref.getDef();
	}

	private Map<Feature, COREFeatureSelectionStatus> runAlgo(
			HashMap<Feature, Evaluation> eval) {
		algo = new FeatureModelStrategyAlgorithm();
		algo.clearAllAutoSelectedFeatures(strategy);
		algo.autoSelectAllMandatoryFeatures(strategy);
		algo.init(strategy, eval);
		EvaluationStrategyManager.getInstance().setStrategy(strategy);

		Map<Feature, COREFeatureSelectionStatus> fr = new HashMap<Feature, COREFeatureSelectionStatus>();
		int evalResult;

		for (Map.Entry<Feature, Evaluation> entry : eval.entrySet()) {
			Feature f = entry.getKey();
			Evaluation userEval = entry.getValue();
			Evaluation ttt = EvaluationStrategyManager.getInstance()
					.getEvaluationObject(f);

			if (userEval.equals(ttt)) {
				System.out.println("hi");
			}

			evalResult = algo.getEvaluation(f);
			COREFeatureSelectionStatus selectionStatus;
			String color = IntentionalElementEditPart.determineColor(urn, f,
					ttt, false, IGRLStrategyAlgorithm.EVAL_FEATURE_MODEL);
			boolean warning = IntentionalElementEditPart
					.determineOverriddenWarning(f,
							IGRLStrategyAlgorithm.EVAL_FEATURE_MODEL)
					|| IntentionalElementEditPart.determineOrXorWarning(f,
							IGRLStrategyAlgorithm.EVAL_FEATURE_MODEL);
			selectionStatus = COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED;

			if (warning)
				// TODO needs to differentiate between user selected and auto
				// selected
				selectionStatus = COREFeatureSelectionStatus.WARNING_USER_SELECTED;
			else if (evalResult == 0
//			 if (evalResult == 0
					&& (FeatureUtil.containsOnlySrcLinkToNotSelectedFeature(f)
							|| FeatureUtil
									.containsOnlyOptionalSrcLinkToFeature(f) || FeatureUtil
								.hasSelectedOrXorBrother(f, true, true)))
				selectionStatus = COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION;
			else if (evalResult == 100) {
				if (userEval.getEvaluation() == 100)
					selectionStatus = COREFeatureSelectionStatus.USER_SELECTED;
				else
					selectionStatus = COREFeatureSelectionStatus.AUTO_SELECTED;
			}

			// if (warning)
			// // TODO needs to differentiate between user selected and auto
			// selected
			// selectionStatus =
			// COREFeatureSelectionStatus.WARNING_USER_SELECTED;
			// else if (color.equals("169,169,169"))
			// selectionStatus =
			// COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION;
			// else if (color.equals("96,255,96")) {
			// if (userEval.getEvaluation()==100)
			// selectionStatus = COREFeatureSelectionStatus.USER_SELECTED;
			// else
			// selectionStatus = COREFeatureSelectionStatus.AUTO_SELECTED;
			// }
			fr.put(f, selectionStatus);
		}

		return fr;

	}

	// DECOMPOSITION: AND
	@Test
	public void testCase1_3() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature subroot = createFeature(root, "subroot", LinkType.OR, 0);

		// AND tree
		Feature child1 = createFeature(subroot, "child1", LinkType.AND, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(subroot, "child2", LinkType.AND, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// tc1 ALL selected
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertEquals(COREFeatureSelectionStatus.AUTO_SELECTED, ss);

				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// tc2 ONE selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
		}

		// tc3 NO selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
		}

	}

	// DECOMPOSITION: OR
	@Test
	public void testCase4_6() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature subroot = createFeature(root, "subroot", LinkType.OR, 0);

		// OR tree
		Feature child1 = createFeature(subroot, "child1", LinkType.OR, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(subroot, "child2", LinkType.OR, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// tc4 ALL selected
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// tc5 ONE selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// tc6 NO selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
		}

	}

	// DECOMPOSITION: XOR
	@Test
	public void testCase7_9() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature subroot = createFeature(root, "subroot", LinkType.OR, 0);

		// XOR tree
		Feature child1 = createFeature(subroot, "child1", LinkType.XOR, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(subroot, "child2", LinkType.XOR, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// tc7 ALL selected -- bug here with both items in an XOR selected
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.WARNING_USER_SELECTED == ss);
//				 assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.WARNING_USER_SELECTED == ss);
//				 assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// tc8 ONE selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// tc9 NO selected
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(subroot, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("subroot")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:1M0O
	@Test
	public void testCase10_11() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// tc11 ALL selected
		eval.put(child1, selected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:1M1O
	@Test
	public void testCase12_14() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// tc1 ALL selected
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			// if (cf.getName().equals("root")) {
			// assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			// }
		}
	}

	// FEATURE CONTRIBUTION:1M2O
	@Test
	public void testCase15_20() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());
		Feature child3 = createFeature(root, "child3", LinkType.OPTIONAL, 0);
		assertEquals("child3", child3.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 15
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 16
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 17
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 18
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, notSelected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 19
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// 20
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:2M0O
	@Test
	public void testCase21_23() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 21
		eval.put(child1, selected);
		eval.put(child2, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 22
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 23
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, notSelected);
		eval.put(child2, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:2M1O
	@Test
	public void testCase24_29() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());
		Feature child3 = createFeature(root, "child3", LinkType.OPTIONAL, 0);
		assertEquals("child3", child3.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 24
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 25
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 26
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 27
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 28
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 29
		eval = new HashMap<Feature, Evaluation>();

		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:2M2O
	@Test
	public void testCase30_38() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());
		Feature child3 = createFeature(root, "child3", LinkType.OPTIONAL, 0);
		assertEquals("child3", child3.getName());
		Feature child4 = createFeature(root, "child4", LinkType.OPTIONAL, 0);
		assertEquals("child4", child4.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 30
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}

		// 31
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 32
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);
		eval.put(child4, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 33
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 34
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 35
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);
		eval.put(child4, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 36
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 37
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, selected);
		eval.put(child4, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
		// 38
		eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);
		eval.put(child4, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child4")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 0);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}

			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}
	}

	// FEATURE CONTRIBUTION:0M1O
	@Test
	public void testCase16() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.OPTIONAL, 0);
		assertEquals("child1", child1.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 39
		eval.put(child1, selected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// 40
		eval.put(child1, notSelected);
		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);

			}
		}
	}

	// FEATURE CONTRIBUTION:0M2O
	@Test
	public void testCase41_43() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.OPTIONAL, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(root, "child2", LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 41
		eval.put(child1, selected);
		eval.put(child2, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}
		// 42
		eval.put(child1, selected);
		eval.put(child2, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}

		// 43
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);
			}
		}

	}
	
	public void testCase46(){
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.AND, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(root, notSelected);
		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
		}
		
	}
	
	

	@Test
	public void testCase444547() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.OPTIONAL, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 44
		eval.put(child1, selected);
		eval.put(child2, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
		}
		// 45
		eval.put(child1, notSelected);
		eval.put(child2, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
		}
		// 47
		eval.put(root, notSelected);
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
		}
		

	}
	
	public void testCase485052(){
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.AND, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());
		Feature child3 = createFeature(child1, "child3", LinkType.MANDATORY, 0);
		assertEquals("child3", child3.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());
		
		//48
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
		}
		// 50
		 eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, selected);
		eval.put(child2, selected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
		}
		// 52
		 eval = new HashMap<Feature, Evaluation>();
		eval.put(root, notSelected);
		eval.put(child1, selected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);


		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
		}
	}
	

	public void testCase495153() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.OPTIONAL, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.MANDATORY, 0);
		assertEquals("child2", child2.getName());
		Feature child3 = createFeature(child1, "child3", LinkType.MANDATORY, 0);
		assertEquals("child3", child3.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 49
		 eval = new HashMap<Feature, Evaluation>();
		eval.put(root, selected);
		eval.put(child1, notSelected);
		eval.put(child2, selected);
		eval.put(child3, notSelected);


		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_ACTION_REQUIRED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
		}

		// 51
		 eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, selected);
		eval.put(child3, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
		}

		// 53
		 eval = new HashMap<Feature, Evaluation>();
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);
		eval.put(child3, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child3")) {
				int value = ((MandatoryFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 50);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
		}

	}
	
	public void testCase5456() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.AND, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());
		// 54
		eval.put(child1, selected);
		eval.put(child2, selected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}

		}
	
		// 56
		eval.put(root, selected);
		eval.put(child1, selected);
		eval.put(child2, notSelected);


		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}

		}
		
	}


	public void testCase5557() {
		Feature root = setupTest();
		Map<Feature, COREFeatureSelectionStatus> fr;
		Iterator<Feature> it;

		Feature child1 = createFeature(root, "child1", LinkType.OPTIONAL, 0);
		assertEquals("child1", child1.getName());
		Feature child2 = createFeature(child1, "child2", LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());

		GrlFactory factory = GrlFactoryImpl.init();
		HashMap<Feature, Evaluation> eval = new HashMap<Feature, Evaluation>();
		strategy = factory.createEvaluationStrategy();
		Evaluation notSelected = factory.createEvaluation();
		Evaluation selected = factory.createEvaluation();
		notSelected.setEvaluation(0);
		selected.setEvaluation(100);
		strategy.setGrlspec(urn.getGrlspec());

		// 55
		eval.put(root, notSelected);
		eval.put(child1, notSelected);
		eval.put(child2, selected);


		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}

		}

		// 57
		eval.put(child1, notSelected);
		eval.put(child2, notSelected);

		eval.put(root, notSelected);

		fr = runAlgo(eval);
		it = fr.keySet().iterator();
		while (it.hasNext()) {
			Feature cf = it.next();
			COREFeatureSelectionStatus ss = fr.get(cf);
			// Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);

			}
			if (cf.getName().equals("child2")) {
				int value = ((OptionalFMLink) (cf.getLinksSrc().get(0)))
						.getQuantitativeContribution();
				assertTrue(value == 100);
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}

		}
	}
}
