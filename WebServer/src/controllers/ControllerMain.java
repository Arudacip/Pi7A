package controllers;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

import controllers.sockets.SocketAdmin;
import models.AbstractLog;
import models.LogAcc;
import models.LogSrv;
import models.services.ServiceLogAcc;
import models.services.ServiceLogSrv;
import models.utils.ConnectorDB;
import views.ViewServiceAdmin;
import views.buttons.ButtonTypes;

/**
 * Classe em design pattern Singleton, que gera o Controller principal do servidor, em design pattern MVC.
 * @author Grupo ECP7AN-MCA1-09 - Bruno Gama, Guilherme Sant'Clair, Luis Felipe, Rafael Cassiolato, Raiza Morata.
 *
 * @param INSTANCE : [CONSTANT] instancia Singleton do Controller
 * @param PORTA_CLIENT : [CONSTANT] porta de acesso default para clientes
 * @param PORTA_SERVER : [CONSTANT] porta de acesso default para administradores
 * @param STOPPED : [CONSTANT] status do service web parado
 * @param UNKOWN : [CONSTANT] status do service web desconhecido
 * @param SRV : [CONSTANT] LogType para logs de SERVIDOR
 * @param ACC : [CONSTANT] LogType para logs de ACESSO
 * @param DEBUG : [CONSTANT] define se os logs de console devem ser <b>verbose</b>
 * @param PORTA : porta em uso no servidor
 * @param servidor : ponteiro para o servidor
 * 
 * @param srvlog : service de logs de SERVIDOR
 * @param srvlog : service de logs de ACESSO
 * @param srvlog : lista de logs de SERVIDOR
 * @param acclog : lista de logs de ACESSO
 * @param mainlog : lista de logs completa
 * 
 * @param viewSAUI : ponteiro para o View de ServiceAdmin
 */

public final class ControllerMain implements ActionListener
{

    // VARIAVEIS DE AMBIENTE
	private static final ControllerMain INSTANCE = new ControllerMain();
	private static final int PORTA_DEFAULT = 80;
    public static final int STARTED = 1;
    public static final int STOPPED = 2;
    public static final int UNKOWN = 3;
	public static final int SRV = 1;
	public static final int ACC = 2;
    public static final boolean DEBUG = false;
    //public static final boolean VERBOSE = true;
    private static String VERSION;
    private static int PORTA;
    private static String PATH;
    private SocketAdmin servidor;
	private AbstractLog currentLog;
    private Connection conn;
	
    // MODELS
    private ServiceLogSrv serviceLS;
    private ServiceLogAcc serviceLA;
    private ArrayList<AbstractLog> srvlog;
    private ArrayList<AbstractLog> acclog;
    private ArrayList<AbstractLog> mainlog;

    // VIEWS
    private static ViewServiceAdmin viewSAUI;
    
    private ControllerMain()
    {
    	Properties prop;
		try {
			// Recupera a porta de acesso ao serverweb
			prop = getProp();
			PORTA = Integer.parseInt(prop.getProperty("prop.server.porta"));
			VERSION = prop.getProperty("prop.server.version");
			String local = prop.getProperty("prop.server.uselocal");
			if (DEBUG)
			{
				System.out.println("SYSINFO: Porta encontrada na config: " + Integer.parseInt(prop.getProperty("prop.server.porta")));
			}
			if (local.equals("true"))
			{
				PATH = System.getProperty("user.dir")+System.getProperty("file.separator");
			} else {
				PATH = "."+System.getProperty("file.separator");
			}
			
			// Recupera as configs de BD e instancia a o ConnectorDB
			conn = null;
			ConnectorDB db = new ConnectorDB();
			conn = db.getConnection(prop);
			conn.setAutoCommit(false);
			if (DEBUG)
			{
				System.out.println("SYSINFO: " + "DB conectado: " + conn.toString());
			}
		} catch (IOException ioe) {
			// Trata o erro, se ocorrer
			if (DEBUG)
			{
				System.out.println("SYSERROR/IO: " + ioe.getMessage());
			}
			PORTA = PORTA_DEFAULT;
		} catch (SQLException sqle) {
			// Trata o erro, se ocorrer
			if (DEBUG)
			{
				System.out.println("SYSERROR/SQL: " + sqle.getMessage());
			}
		} catch (InterruptedException e) {
			// Trata o erro, se ocorrer
			if (DEBUG)
			{
				System.out.println("SYSERROR/ITR: " + e.getMessage());
			}
		}
    }
    
    /**
     * Retorna a instancia unica do Controller principal do servidor.
     * @return Instancia unica do Controller
     */
    public static ControllerMain getInstance()
    {
    	return INSTANCE;
    }
    
    /**
     * Prepara as variaveis de ambiente necessarias para funcionamento e cria o service web HTTP no servidor.
     */
    public void createService()
    {
        // Cria os Models
    	srvlog = new ArrayList<AbstractLog>();
    	acclog = new ArrayList<AbstractLog>();
    	mainlog = new ArrayList<AbstractLog>();
    	srvlog.clear();
    	acclog.clear();
    	mainlog.clear();
    	
        // Services montam os logs recuperados no database
    	serviceLS = new ServiceLogSrv(conn);
        serviceLA = new ServiceLogAcc(conn);
        srvlog = serviceLS.listaUltimos(20);
        acclog = serviceLA.listaUltimos(20);
        mainlog.addAll(srvlog);
        mainlog.addAll(acclog);
        if (DEBUG)
        {
			System.out.println("LOGSRV: " + srvlog.toString());
			System.out.println("LOGACC: " + acclog.toString());
		}
    	Collections.sort(mainlog);
    	for (int i = 0; i < 35; i++) {
        	mainlog.remove(0);
        }
        for (AbstractLog log : mainlog)
        {
        	viewSAUI.addLog(log);
        }
        
        servidor = new SocketAdmin();
        if (DEBUG)
		{
			System.out.println("SYSINFO: " + servidor.toString());
		}
    }
    
    /**
     * Constroi as Views, em design pattern MVC.
     */
    public void createView()
    {
        viewSAUI = new ViewServiceAdmin(VERSION);
        viewSAUI.setVisible(true);
    }
    
    /**
     * Inicializar o service web em uma Thread, separadamente da View.
     */
    private void startService()
    {
    	// Usa uma Thread, para nao travar a view
    	new Thread()
    	{
            @Override
            public void run()
            {
            	generateLog(SRV, "TryStart");
                try
                {
                	servidor.start(PORTA, PATH);
                	generateLog(SRV, "Started!");
                	// tem que ser checado aqui caso contrario da null pointer exception
                    checkStatus();
                } catch (IOException ex) {
                    if (DEBUG)
                    {
                    	System.out.println(ex.getMessage());
                    }
                	generateLog(SRV, "NotStarted");
                	// tem que ser checado aqui caso contrario da null pointer exception
                    checkStatus();
                }
                servidor.esperarCliente();
            }
        }.start();
    }
    
    /**
     * Parar o service web.
     */
    private void stopService()
    {
        generateLog(SRV, "TryStop");
        boolean sucesso = servidor.stop();
        if (sucesso)
        {
        	generateLog(SRV, "Stopped!");
        	// tem que ser checado aqui caso contrario da null pointer exception
            checkStatus();
        } else
        {
        	generateLog(SRV, "NotStopped");
        	// tem que ser checado aqui caso contrario da null pointer exception
            checkStatus();
        }
    }
    
    /**
     * Reiniciar o service web.
     */
    private void restartService()
    {
    	generateLog(SRV, "TryRestart");
        stopService();
        startService();
        generateLog(SRV, "Restarted!");
    }
    
    /**
     * Verificar o status do service web.
     */
    private void checkStatus()
    {
    	int valor = servidor.getStatus();
    	switch (valor)
    	{
    	case ControllerMain.STARTED:
    		viewSAUI.setStatus(ControllerMain.STARTED);
    		break;
    	case ControllerMain.STOPPED:
            viewSAUI.setStatus(ControllerMain.STOPPED);
    		break;
    	case ControllerMain.UNKOWN:
            viewSAUI.setStatus(ControllerMain.UNKOWN);
    		break;
    	default:
            viewSAUI.setStatus(ControllerMain.UNKOWN);
    		break;
    	}
    }
    
    /**
     * Recebe os eventos da View e executa as acoes necessarias de acordo com o caso.
     */
	@Override
	public void actionPerformed(ActionEvent e)
	{
		// Iniciar
        if (e.getSource() == viewSAUI.getIteractions().getButton(ButtonTypes.Start))
        {
            startService();
        }
        // Reiniciar
        else if (e.getSource() == viewSAUI.getIteractions().getButton(ButtonTypes.Restart))
        {
        	restartService();
        }
        // Parar
        else if (e.getSource() == viewSAUI.getIteractions().getButton(ButtonTypes.Stop))
        {
            stopService();
        }
	}
	
	/**
	 * Gera os objetos AbstractLog do service web, de acordo com os tipos e parametros necessarios.
	 * 
	 * @param tipo : tipo de AbstractLog
	 * @param texto : mensagem do log
	 */
	public void generateLog(int tipo, String texto)
	{
		switch(tipo)
		{
		case SRV:
			currentLog = new LogSrv(new Date(System.currentTimeMillis()), texto);
			srvlog.add(currentLog);
			mainlog.add(currentLog);
			viewSAUI.addLog(currentLog);
			// Registra o log no DB
			serviceLS.incluir(currentLog);
			break;
			
		case ACC:
			currentLog = new LogAcc(new Date(System.currentTimeMillis()), texto);
			acclog.add(currentLog);
			mainlog.add(currentLog);
			viewSAUI.addLog(currentLog);
			// Registra o log no DB
			serviceLA.incluir(currentLog);
			break;
			
		default:
			System.out.println("SYSERROR: Erro Desconhecido");
			currentLog = new LogSrv(new Date(System.currentTimeMillis()), "UnkErr");
			srvlog.add(currentLog);
			mainlog.add(currentLog);
			viewSAUI.addLog(currentLog);
			// Registra o log no DB
			serviceLS.incluir(currentLog);
			break;
		}
	}

	/**
	 * Retorna as propriedades definidas no arquivo de configuracao do webserver.
	 * @return Propriedades do webserver
	 * @throws IOException
	 */
	public static Properties getProp() throws IOException
	{
        Properties props = new Properties();
        FileInputStream file = new FileInputStream("./info.properties");
        props.load(file);
        return props;
    }

	/**
	 * Retorna o service de LogAcc do webserver.
	 * @return ServiceLogAcc do webserver
	 */
	public ServiceLogAcc getServiceAcc()
	{
        return serviceLA;
    }

	/**
	 * Retorna o service de LogSrv do webserver.
	 * @return ServiceLogSrv do webserver
	 */
	public ServiceLogSrv getServiceSrv()
	{
        return serviceLS;
    }
}