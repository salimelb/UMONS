package gui.ctrl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.yakindu.scr.vendingmachine.IVendingMachineStatemachine.SCInterfaceListener;
import org.yakindu.scr.vendingmachine.IVendingMachineStatemachine.SCInterfaceOperationCallback;

import framework.Article;
import framework.Category;
import framework.Machine;
import framework.RawMaterial;
import framework.modules.Module;
import framework.payement.Carte;
import framework.payement.Coin;
import framework.payement.Payment;
import framework.payement.Token;
import gui.MainApp;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
/**
 * 
 * @author Copois Pierre
 * @author Zaretti Steve
 * 
 */
@SuppressWarnings({"unused"})
public class VueUtilisateur extends Pane  {    
    
	private Stage mainApp, stage;
	private Scene scene;
	private Machine machine;
	private Category focusCategory;
	private Article focusArticle;
	private boolean pass;
	private Coin coin = null;
	
    public VueUtilisateur(Stage parent, Machine machine) {
        this.mainApp = parent;
        this.machine = machine;
        this.focusCategory = machine.getCategory();
        this.focusArticle = null;
        
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/views/VueUtilisateur.fxml"));
        fxmlLoader.setController(this);
        
        try {
        	stage = new Stage();
            scene = new Scene(fxmlLoader.load()); 
            stage.setTitle("Cr�ation d'une nouvelle machine");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL);

        	initialize(stage);
        	bindYakindo();
        	
        	stage.setOnCloseRequest((WindowEvent event) -> {
	        	MainApp.getState().getSCInterface().raiseMaintenance();
	    		MainApp.getState().runCycle();
	        	stage.close();
        	});
        	parent.toBack();
        	stage.show();
        	
        } catch (IOException e) {
        	
        }
    }
    private void bindYakindo() {
    	MainApp.getState().getSCInterface().getListeners().clear();
    	MainApp.getState().getSCInterface().getListeners().add(new SCInterfaceListener() {
			@Override
			public void onRefoundRaised() {
				if( coin != null ) {
					ArrayList<Integer> c = coin.refund((int)MainApp.getState().getSCInterface().getTotalPaid());
					focusArticle = null;
					MainApp.getState().getSCInterface().setTotalPaid(0);
					Platform.runLater(() -> updatePayement());
					Platform.runLater(() -> Utils.showPopUp(c));
				}
			}
    	});
    	MainApp.getState().getSCInterface().setSCInterfaceOperationCallback(new SCInterfaceOperationCallback() {
			public boolean validate() {
				if( pass ) { pass = false; return true; }
				return false;
			}
    	});
    }
    
    private void initialize(Stage stage) {
    	
    	TabPane tp = (TabPane)scene.lookup("#tabCategory");
    	tp.getTabs().clear();
    	
    	Category c = focusCategory;
    	do {
    		Tab tab = new Tab(c.toString());
        	FlowPane p = new FlowPane();
        	
        	if( c.getCategories() != null ) {
        		for( Category c2 : c.getCategories() ) {
        			Pane n = Utils.addNewArticle(p, c2);
        			n.setOnMousePressed((MouseEvent e) -> { focusCategory = c2; initialize(stage); });
        		}
        	}
    		
        	if( c.getArticles() != null ) {
        		for( Article a : c.getArticles() ) {
        			Pane n = Utils.addNewArticle(p, a);
        			n.setOnMousePressed((MouseEvent e) -> {
                	    MainApp.getState().getSCInterface().setItemPrice(a.getPrice());
                	    MainApp.getState().getSCInterface().raiseAddItem();
                	    MainApp.getState().runCycle();
                	    	
                	    focusArticle = a;
                	    updatePayement();
                	    
                	    if( coin != null ) validatePay(coin);
        			});
        		}
    		}
        	
        	tab.setContent(p);
        	tp.getTabs().add( tab );
        	
        	c = c.getParent();
    	} while( c != null );
    	
    	((ImageView)scene.lookup("#PayByCard")).setVisible(false);
    	((ImageView)scene.lookup("#PayByCoin")).setVisible(false);
    	((ImageView)scene.lookup("#PayByTokken")).setVisible(false);
    	for( Module m : machine.getModules() ) {
			if( m instanceof Payment ) {
				if( m instanceof Carte ) {
					((ImageView)scene.lookup("#PayByCard")).setVisible(true);
					((Node)scene.lookup("#PayByCard")).setUserData((Payment)m);
				}
				else if( m instanceof Coin ) {
					coin = (Coin) m;
					((ImageView)scene.lookup("#PayByCoin")).setVisible(true);
					((Node)scene.lookup("#PayByCoin")).setUserData((Payment)m);
				}
				else if( m instanceof Token ) {
					((ImageView)scene.lookup("#PayByTokken")).setVisible(true);
					((Node)scene.lookup("#PayByTokken")).setUserData((Payment)m);
				}
			}
    	}
    }
    private void updatePayement() {
    	if( focusArticle != null ) {
    		((ImageView)scene.lookup("#image")).setVisible(true);
    		((Label)scene.lookup("#name")).setVisible(true);
    		((Label)scene.lookup("#price")).setVisible(true);
    		
    		((ImageView)scene.lookup("#image")).setImage(new Image(focusArticle.getImage().toURI().toString()));
    		((Label)scene.lookup("#name")).setText(focusArticle.getName());
    		((Label)scene.lookup("#price")).setText(((float)(focusArticle.getPrice())/100)+" �");
    		((Pane)scene.lookup("#idPane")).getChildren().clear();
    		for(RawMaterial obj: focusArticle.getRecipe()) {
    			if( obj.getMin() == obj.getMax() )
    				continue;
    			Label label = new Label(obj.getName());
    			label.setTextFill(Color.web("#0076a3"));
    			Slider slider = new Slider(obj.getMin(), obj.getMax(), obj.getAmount());
    			slider.setShowTickLabels(true);
    			slider.setShowTickMarks(true);
    			slider.setSnapToTicks(true);
    			slider.setMajorTickUnit(1);
    			slider.setMinorTickCount(0);
    			slider.setBlockIncrement(1.0);
    			slider.valueProperty().addListener((observable, oldValue, newValue) -> {obj.setAmount(newValue.intValue());});
    			((Pane)scene.lookup("#idPane")).getChildren().add(label);
    			((Pane)scene.lookup("#idPane")).getChildren().add(slider);
    		}
    	}
    	else {
    		((ImageView)scene.lookup("#image")).setVisible(false);
    		((Label)scene.lookup("#name")).setVisible(false);
    		((Label)scene.lookup("#price")).setVisible(false);
    		((Pane)scene.lookup("#idPane")).getChildren().clear();
    		
    	}
    	
    	((Label)scene.lookup("#solde")).setText("Solde: "+(float)(MainApp.getState().getSCInterface().getTotalPaid())/100+" �");
    }
    @FXML
    private void OnClick_Buy(Event e) {
       	Payment p = (Payment)((Node)e.getTarget()).getUserData();
       	
       	if( p instanceof Coin ) {
    		Coin c = (Coin)p;
    		
    		ChoiceDialog<Integer> dialog = new ChoiceDialog<Integer>();
	    	dialog.getItems().setAll(c.getModules());
	    	dialog.setTitle("Choisissez une pi�ce");
	    	dialog.setHeaderText("Choisissez une pi�ce");
	    	Optional<Integer> result = dialog.showAndWait();
	    	if (result.isPresent()){
	    		if( c.insertPiece(result.get()) ) {
	    			MainApp.getState().getSCInterface().setPiece( result.get());
	    			MainApp.getState().getSCInterface().raiseInsertPiece();
	    			MainApp.getState().runCycle();
	    		}
	    		updatePayement();
	    	}
    	}
       	validatePay(p);
    }
    private void validatePay(Payment p) {
    	if( focusArticle != null  ) {
    		Machine.Delivery d =  machine.Buy(p, focusArticle, Math.toIntExact(MainApp.getState().getSCInterface().getTotalPaid()));
    		
    		if( d.getArticle() != null ) {
    			pass = true;
    			Alert alert = new Alert(AlertType.CONFIRMATION);
    	    	alert.setTitle("Achat");
    	    	alert.setHeaderText("Votre achat s'est d�roul� avec succ�s.");
    	    	alert.showAndWait();
    	    	
    	    	Utils.showPopUp(d.getArticle());
    	    	if( d.getOther() != null && d.getOther() instanceof ArrayList )
    	    		Utils.showPopUp(d.getOther());
    	    }
    		else if(Math.toIntExact(MainApp.getState().getSCInterface().getTotalPaid()) < focusArticle.getPrice()){
    			
    		}
    		else {
    			Alert alert = new Alert(AlertType.ERROR);
    	    	alert.setTitle("Erreur");
    	    	alert.setHeaderText("Le paiement a �chou�.");
    	    	alert.show();
    		}
    	}
    }
    @FXML
    private void OnClick_Cancel() {
    	focusCategory = null;
    	MainApp.getState().getSCInterface().raiseForceRefound();
    }
}