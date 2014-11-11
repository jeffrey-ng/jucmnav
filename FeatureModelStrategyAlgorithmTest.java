package seg.jUCMNav.tests;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import grl.EvaluationStrategy;
import grl.GRLGraph;
import grl.GRLNode;
import grl.IntentionalElement;
import grl.IntentionalElementRef;
import grl.StrategiesGroup;

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
import seg.jUCMNav.core.COREFactory4URN;
import seg.jUCMNav.editors.UCMNavMultiPageEditor;
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

public class FeatureModelStrategyAlgorithmTest extends TestCase{

	 enum LinkType {
	    MANDATORY, OPTIONAL, XOR, OR,
	    AND
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
			testFile.delete(true, false,null);
		}
		
		testFile.create(new ByteArrayInputStream("".getBytes()), false, null);
		 IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
	     IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(testFile.getName());
	     editor = (UCMNavMultiPageEditor) page.openEditor(new FileEditorInput(testFile), desc.getId());
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
	        DeletePreferences.getPreferenceStore().setValue(DeletePreferences.PREF_DELDEFINITION, DeletePreferences.PREF_ALWAYS);
	        DeletePreferences.getPreferenceStore().setValue(DeletePreferences.PREF_DELREFERENCE, DeletePreferences.PREF_ALWAYS);
		
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
		StrategyEvaluationPreferences.setAlgorithm(StrategyEvaluationPreferences.FEATURE_MODEL_ALGORITHM + "");
		StrategyEvaluationPreferences.setTolerance(0);
		StrategyEvaluationPreferences.getPreferenceStore().setValue("PREF_AUTOSELECTMANDATORYFEATURES", true);

		Iterator it4 = urn.getUrndef().getSpecDiagrams().iterator();
		while (it4.hasNext()) {
			IURNDiagram diagram = (IURNDiagram) it4.next();
			if (diagram instanceof FeatureDiagram)
				fd = (FeatureDiagram) diagram;
		}
		if (root == null) {
			IntentionalElementRef ref = (IntentionalElementRef) ModelCreationFactory.getNewObject(urn, IntentionalElementRef.class, ModelCreationFactory.FEATURE);
	        AddIntentionalElementRefCommand aierCmd = new AddIntentionalElementRefCommand(fd, ref);
	        aierCmd.execute();		
		}
		List<Feature> roots = FeatureUtil.getRootFeatures(fm.getGrlspec());
		if (!roots.isEmpty()) {
			// root feature exists (take the first one as URN does not constrain feature models to one root), but is it placed on a feature diagram?
			root = roots.get(0);
		}
		root.setName("root");
		this.fd = fd;
		this.fm = fm;
		return root;

	}
	
	private Feature createFeature(IntentionalElement parent, String name, LinkType relationship, int numericalContribution){
		IntentionalElementRef ref = (IntentionalElementRef) ModelCreationFactory.getNewObject(urn, IntentionalElementRef.class, ModelCreationFactory.FEATURE);
		AddIntentionalElementRefCommand aierCmd = new AddIntentionalElementRefCommand(fd, ref);
		aierCmd.execute();
		ChangeGrlNodeNameCommand cgnnCmd = new ChangeGrlNodeNameCommand(ref, name);
		cgnnCmd.execute();

		ElementLink link = null;
		int type = 0;
		if (relationship == LinkType.MANDATORY) {
			// add mandatory link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn, MandatoryFMLink.class);								
		} else if (relationship == LinkType.OPTIONAL) {
			// add optional link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn, OptionalFMLink.class);
		} else if (relationship == LinkType.XOR) {
			// add XOR decomposition link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn, Decomposition.class);
			type = 2;
		} else if (relationship == LinkType.OR) {
			// add OR decomposition link between this feature and the new child feature
			link = (ElementLink) ModelCreationFactory.getNewObject(urn, Decomposition.class);
			type = 1;
		} else if (relationship == LinkType.AND) {
			link = (ElementLink) ModelCreationFactory.getNewObject(urn, Decomposition.class);
			type = 0;
		}
		
		CreateElementLinkCommand celCmd = new CreateElementLinkCommand(urn, (IntentionalElement) ref.getDef(), link);
		celCmd.setTarget(parent);
		if (celCmd.canExecute())
			celCmd.execute();
		if (relationship == LinkType.MANDATORY || relationship == LinkType.OPTIONAL) {
			ChangeNumericalContributionCommand cncmd3 = new ChangeNumericalContributionCommand(link.getRefs(),0,numericalContribution,cs);
			cncmd3.execute();
		}
		
		if (relationship == LinkType.XOR || relationship == LinkType.OR || relationship == LinkType.AND) {
			ChangeDecompositionTypeCommand cdtCmd = new ChangeDecompositionTypeCommand((IntentionalElementRef) parent.getRefs().get(0), type);
			if (cdtCmd.canExecute())
				cdtCmd.execute();
		}
		
		return (Feature) ref.getDef();
	}
	
	
	@Test
	public void testCase(){
		Feature root = setupTest();
		List<COREFeature> features = new ArrayList<COREFeature>();

		Feature child1 = createFeature(root,"child1",LinkType.MANDATORY, 0);
		assertEquals("child1", child1.getName());
		
		Feature child11 = createFeature(child1,"child11",LinkType.XOR, 0);
		assertEquals("child11", child11.getName());
		features.add((COREFeature) child11);
		
		Feature child12 = createFeature(child1,"child12",LinkType.XOR, 0);
		assertEquals("child12", child12.getName());

		Feature child2 = createFeature(root,"child2",LinkType.OPTIONAL, 0);
		assertEquals("child2", child2.getName());
		
		Feature child21 = createFeature(child2,"child21",LinkType.OPTIONAL, 0);
		assertEquals("child21", child21.getName());
		features.add((COREFeature) child21);
		
		Feature child3 = createFeature(root,"child3",LinkType.MANDATORY, 2);
		child3.getLinksSrc().get(0);
		assertEquals("child3", child3.getName());
		
		Feature child31 = createFeature(child3,"child31",LinkType.OR, 0);
		assertEquals("child31", child31.getName());
		features.add((COREFeature) child31);
		
		Feature child32 = createFeature(child3,"child32",LinkType.OR, 0);
		assertEquals("child32", child32.getName());

		Feature child4 = createFeature(root,"child4",LinkType.MANDATORY, 2);
		assertEquals("child4", child4.getName());
		
		
		Feature child41 = createFeature(child4,"child41",LinkType.AND, 0);
		assertEquals("child41", child41.getName());
		features.add((COREFeature) child41);
		
		Feature child42 = createFeature(child4,"child42",LinkType.AND, 0);
		assertEquals("child42", child42.getName());

		EvaluationResult er = ((FeatureModelImpl) fm).select(features);

		Iterator<COREFeature> it5 = er.featureResult.keySet().iterator();
		while (it5.hasNext()) {
			COREFeature cf = it5.next();
			COREFeatureSelectionStatus ss = er.featureResult.get(cf);
			//Check each feature and if they're selected or not.
			if (cf.getName().equals("child1")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
			if (cf.getName().equals("child11")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child12")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("child2")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
			if (cf.getName().equals("child21")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child3")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
			if (cf.getName().equals("child31")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child32")) {
				assertTrue(COREFeatureSelectionStatus.NOT_SELECTED_NO_ACTION == ss);
			}
			if (cf.getName().equals("child4")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
			if (cf.getName().equals("child41")) {
				assertTrue(COREFeatureSelectionStatus.USER_SELECTED == ss);
			}
			if (cf.getName().equals("child42")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
			if (cf.getName().equals("root")) {
				assertTrue(COREFeatureSelectionStatus.AUTO_SELECTED == ss);
			}
		}		
		
	}
	
}

