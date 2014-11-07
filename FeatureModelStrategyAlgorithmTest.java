package seg.jUCMNav.tests;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fm.Feature;
import fm.FeatureDiagram;
import fm.FeatureModel;
import fm.FmFactory;
import fm.MandatoryFMLink;
import fm.OptionalFMLink;
import fm.impl.FeatureModelImpl.EvaluationResult;
import grl.ActorRef;
import grl.Belief;
import grl.ContributionType;
import grl.ElementLink;
import grl.EvaluationStrategy;
import grl.GRLGraph;
import grl.GRLNode;
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
import seg.jUCMNav.core.COREFactory4URN;
import seg.jUCMNav.editors.UCMNavMultiPageEditor;
import seg.jUCMNav.model.ModelCreationFactory;
import seg.jUCMNav.model.commands.create.AddIntentionalElementRefCommand;
import seg.jUCMNav.model.commands.create.CreateFMDCommand;
import seg.jUCMNav.model.commands.create.CreateGrlGraphCommand;
import seg.jUCMNav.model.commands.create.CreateStrategiesGroupCommand;
import seg.jUCMNav.model.commands.create.CreateStrategyCommand;
import seg.jUCMNav.model.commands.delete.DeleteMapCommand;
import seg.jUCMNav.model.commands.transformations.ChangeGrlNodeNameCommand;
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

	private UCMNavMultiPageEditor editor;
    private CommandStack cs;

	
	  private URNspec urnspec;
	    private GRLGraph graph;
	    private IntentionalElementRef ref;
	    private Belief belief;
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
	        graph = ((CreateGrlGraphCommand) cmd).getDiagram();
	        assertTrue("Can't execute CreateGrlGraphCommand.", cmd.canExecute()); //$NON-NLS-1$
	        cs.execute(cmd);

	        // Set the preferences for deleting the references to ALWAYS
	        DeletePreferences.getPreferenceStore().setValue(DeletePreferences.PREF_DELDEFINITION, DeletePreferences.PREF_ALWAYS);
	        DeletePreferences.getPreferenceStore().setValue(DeletePreferences.PREF_DELREFERENCE, DeletePreferences.PREF_ALWAYS);
		
	     
//		GrlFactory factory = GrlFactoryImpl.init();
//		strategy = factory.createEvaluationStrategy();
//		GRLspec spec = factory.createGRLspec();
//		strategy.setGrlspec(spec);
		
	}
	
	@After
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		editor.doSave(null);
		
		editor.closeEditor(false);
	}
	@Test
	public void testGetEvaluationType() {
		URNspec urn = ModelCreationFactory.getNewURNspec(true, true, true);
		FeatureModel fm = urn.getGrlspec().getFeatureModel();
		FeatureDiagram fd = null;
		Feature root = null;
		
		StrategyEvaluationPreferences.createPreferences();
		StrategyEvaluationPreferences.setAlgorithm(StrategyEvaluationPreferences.FEATURE_MODEL_ALGORITHM + "");

		
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
		root.addFeature("child1", COREFeatureRelationshipType.OR);
		Feature child = (Feature) ((IntentionalElementRef) fd.getNodes().get(1)).getDef();
		assertEquals("child1", child.getName());
		child.setSelectable(true);
		root.addFeature("child2", COREFeatureRelationshipType.OR);
		Feature child2 = (Feature) ((IntentionalElementRef) fd.getNodes().get(2)).getDef();
		assertEquals("child2", child2.getName());


		//link = (ElementLink) child.getLinksSrc().get(0);
		

//		Generate root.

		
//		FeatureModel fm = (FeatureModel)ModelCreationFactory.getNewObject(urn, FeatureModel.class);
		
	
		List<COREFeature> features = new ArrayList<COREFeature>();
		StrategiesGroup group = (StrategiesGroup) ModelCreationFactory.getNewObject(urn, StrategiesGroup.class);
		CreateStrategiesGroupCommand csgCmd = new CreateStrategiesGroupCommand(urn, group);
		if (csgCmd.canExecute()){
			csgCmd.execute();
		}else {return;}
		CreateStrategyCommand csCmd = new CreateStrategyCommand(urn, group);
		EvaluationStrategy strategy = csCmd.getStrategy();
		if (csCmd.canExecute())
			csCmd.execute();
		else 
			return;
		// select the new strategy and set the values of the selected features
		EvaluationStrategyManager manager = EvaluationStrategyManager.getInstance();
		manager.setStrategy(strategy);
		manager.calculateEvaluation();
		algo = new FeatureModelStrategyAlgorithm();
		algo.autoSelectAllMandatoryFeatures(strategy);
		
		assertEquals(100,child.getImportanceQuantitative());
		
//		
		//manager.calculateEvaluation();
		
		
		
	}
	
//	@Test
//	public void testGetEvaluationType() {
//		int v = algo.getEvaluationType();
//		assertEquals(v,IGRLStrategyAlgorithm.EVAL_FEATURE_MODEL);
//	}
//	
	
}
