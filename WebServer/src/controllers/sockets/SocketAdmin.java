package controllers.sockets;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import controllers.ControllerMain;
import models.AbstractLog;
import models.ResultTable;

/**
 * Classe que cria o server socket principal do servidor, assim como controla e trata as threads dos usuarios, sob demanda.
 * @author Grupo ECP7AN-MCA1-09 - Bruno Gama, Guilherme Sant'Clair, Luis Felipe, Rafael Cassiolato, Raiza Morata.
 * 
 * @param WEB_ROOT : [CONSTANT] raiz do service web
 * @param DEFAULT_FILE : [CONSTANT] path do arquivo de indice
 * @param UNKNOWN : [CONSTANT] path do arquivo de erro 400 
 * @param FORBIDDEN : [CONSTANT] path do arquivo de erro 403 
 * @param NOT_FOUND : [CONSTANT] path do arquivo de erro 404
 * @param NOT_SUPPORTED : [CONSTANT] path do arquivo de erro 405
 * @param servidor : ponteiro para o servidor
 * @param cliente : ponteiro para o cliente da thread
 */

public class SocketAdmin implements Runnable
{
	
    private static final String DEFAULT_FILE = "index.html";
	private static final String UNKNOWN = "errors/400.html";
    private static final String FORBIDDEN = "errors/403.html";
    private static final String NOT_FOUND = "errors/404.html";
    private static final String NOT_SUPPORTED = "errors/405.html";
    private static File WEB_ROOT = new File("./wwwroot/");
    private String PATH;
	private ServerSocket servidor;
	private Socket cliente;
	
	/**
	 * Construtor principal do SocketAdmin.
	 */
	public SocketAdmin()
	{
	}
	
	/**
	 * Construtor auxiliar do SocketAdmin.
	 * @param cliente : ciente a ser conectado ao service web
	 */
	public SocketAdmin(Socket cliente)
	{
		this.cliente = cliente;
	}
	
	/**
	 * Inicia o server socket para receber as requisicoes dos usuarios.
	 * @param porta : porta onde o service vai "ouvir"
	 * @throws IOException
	 */
	public void start(int porta, String path) throws IOException
	{
		servidor = new ServerSocket(porta);
		this.PATH = path;
		WEB_ROOT = new File(PATH+"wwwroot/");
		if (ControllerMain.DEBUG)
		{
			System.out.println(WEB_ROOT.toString());
		}
	}
	
	/**
	 * Parar o server socket do service web.
	 * @return Se o server socket parou ou nao
	 */
	public boolean stop()
	{
		try
		{
			servidor.close();
			return true;
		} catch (IOException e)
		{
			// Trata a exception
			if (ControllerMain.DEBUG)
			{
				System.out.println("SYSERROR: " + e.getMessage());
			}
			//ControllerMain.getInstance().generateLog(ControllerMain.SRV, "IOError");
			return false;
		}
	}
	
	/**
	 * Checar o status do service web, e portanto do server socket.
	 * @return Se o server socket esta parado ou nao
	 */
	public int getStatus()
	{
		int status = ControllerMain.UNKOWN;
		try
		{
			boolean isdown = servidor.isClosed();
			if (!isdown)
			{
				status = ControllerMain.STARTED;
			} else
			{
				status = ControllerMain.STOPPED;
			}
		} catch (Exception e)
		{
			// Trata a exception
			System.out.println(e.getMessage());
			//status = ControllerMain.UNKOWN;
		}
		return status;
	}
	
	/**
	 * Espera qualquer cliente conectar, e prepara o ambiente quando um cliente novo acessa o service web.
	 * @return Conexao de cliente recebida
	 */
	public Socket esperarCliente()
	{
		cliente = null;
		try
		{
			while (true)
			{
				cliente = servidor.accept();
				SocketAdmin server = new SocketAdmin(cliente);
				if (ControllerMain.DEBUG)
				{
					System.out.println("Conexao aberta. (" + cliente.toString() + ")");
				}
				ControllerMain.getInstance().generateLog(ControllerMain.SRV, "OpenCon");
				// cria a thread dedicada para gerenciar o cliente
				Thread thread = new Thread(server);
				thread.start();
			}
		} catch (IOException e)
		{
			// Trata a exception
			if (ControllerMain.DEBUG)
			{
				System.out.println("CRIT: " + e.getMessage());
			}
			//ControllerMain.getInstance().generateLog(ControllerMain.SRV, "IOError");
		}
		return cliente;
	}
	
	/**
	 * Executa a thread individual de cada cliente.
	 */
	@Override
	public void run()
	{
		// Gerenciando a conexao do cliente individual
		BufferedReader in = null;
		PrintWriter out = null;
		BufferedOutputStream dataOut = null;
		String fileRequested = null;
		String method = "";
		
		try
		{
			// cria reader para entradas de texto do cliente
			in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
			// cria writer para saidas de texto
			out = new PrintWriter(cliente.getOutputStream());
			// cria writer para saidas de dados
			dataOut = new BufferedOutputStream(cliente.getOutputStream());
			
			String input = in.readLine();
			StringTokenizer parse = new StringTokenizer(input);
			method = parse.nextToken().toUpperCase();
			// separa o arquivo solicitado
			fileRequested = parse.nextToken().toLowerCase();
			String ip = cliente.getInetAddress().getHostAddress();
			
			// checa o metodo - suporta apenas GET and HEAD
			if (!method.equals("GET")  &&  !method.equals("HEAD"))
			{
				if (ControllerMain.DEBUG)
				{
					System.out.println("ACC: "+fileRequested+"#"+method+"#"+ip+"#"+"405");
				}
				ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"405");
				
				File file = new File(WEB_ROOT, NOT_SUPPORTED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				byte[] fileData = lerArquivoDados(file, fileLength);
					
				// envia HTTP Headers e dados
				out.println("HTTP/1.1 405 Nao Suportado");
				out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println();
				out.flush();
				// file
				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();
				
			} else
			{
				// GET or HEAD method
				if (ControllerMain.DEBUG)
				{
					System.out.println("SYSINFO: Request: "+fileRequested);
				}
				String subtest = fileRequested.substring(fileRequested.lastIndexOf('/') + 1);
				if (fileRequested.length() > 1 && !subtest.contains("."))
				{
					if (ControllerMain.DEBUG)
					{
						System.out.println("SYSINFO: Request nao contem extensao.");
					}
					fileRequested += "/";
				}
				// Verificar request de pasta
				if (fileRequested.length() > 1 && fileRequested.endsWith("/"))
				{
					if (ControllerMain.DEBUG)
					{
						System.out.println("SYSINFO: Size="+fileRequested.length()+" > Request com / > Request de pasta.");
					}
					ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"403");
					
					File file = new File(WEB_ROOT, FORBIDDEN);
					int fileLength = (int) file.length();
					String contentMimeType = "text/html";
					byte[] fileData = lerArquivoDados(file, fileLength);
						
					// envia HTTP Headers e dados
					out.println("HTTP/1.1 403 Proibido");
					out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
					out.println("Date: " + new Date());
					out.println("Content-type: " + contentMimeType);
					out.println("Content-length: " + fileLength);
					out.println();
					out.flush();
					// file
					dataOut.write(fileData, 0, fileLength);
					dataOut.flush();
				} else
				{
					// Nao e pasta, segue o jogo
					if (fileRequested.endsWith("/"))
					{
						if (ControllerMain.DEBUG)
						{
							System.out.println("SYSINFO: Size="+fileRequested.length()+" > Enviar root file.");
						}
						fileRequested += DEFAULT_FILE;
					}
					// Checa se foi solicitado um relatorio
					String subtest02 = fileRequested.substring(fileRequested.lastIndexOf('/') + 1);
					if (subtest02.contains("relatorio") && subtest02.contains(".htm"))
					{
						// Retornar relatorio
						int fileLength;
						String content = "text/html";
						byte[] fileData;
						
						// RELATORIO 01
						if (subtest02.contains("relatorio01"))
						{
							fileData = relatorio01().getBytes();
							
						// RELATORIO 02
						} else if (subtest02.contains("relatorio02"))
						{							
							fileData = relatorio02().getBytes();
							
						// RELATORIO 03
						} else if (subtest02.contains("relatorio03"))
						{							
							fileData = relatorio03().getBytes();
							
						// RELATORIO 04
						} else if (subtest02.contains("relatorio04"))
						{							
							fileData = relatorio04().getBytes();
							
						// RELATORIO 05
						} else if (subtest02.contains("relatorio05"))
						{							
							fileData = relatorio05().getBytes();
							
						// RELATORIO 06
						} else if (subtest02.contains("relatorio06"))
						{
							fileData = relatorio06().getBytes();
							
						// RELATORIO 07
						} else if (subtest02.contains("relatorio07"))
						{							
							fileData = relatorio07().getBytes();
							
						// RELATORIO 08
						} else if (subtest02.contains("relatorio08"))
						{							
							fileData = relatorio08().getBytes();
							
						// RELATORIO 01 - DEFAULT
						} else if (subtest02.contains("relatorio09"))
						{							
							fileData = relatorio09().getBytes();
							
						// RELATORIO 01 - DEFAULT
						} else if (subtest02.contains("relatorio10"))
						{							
							fileData = relatorio10().getBytes();
							
						// RELATORIO 01 - DEFAULT
						} else
						{							
							File file = new File(WEB_ROOT, fileRequested);
							content = getContentType(fileRequested);
							fileLength = (int) file.length();
							fileData = lerArquivoDados(file, fileLength);
						}
						
						fileLength = (int) fileData.length;
						
						// metodo de conexao GET no protocolo HTTP 
						if (method.equals("GET"))
						{
							// send HTTP Headers
							out.println("HTTP/1.1 200 OK");
							out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
							out.println("Date: " + new Date());
							out.println("Content-type: " + content);
							out.println("Content-length: " + fileLength);
							out.println();
							out.flush();
							
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"200");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"200");
							}
						} else
						{
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"400");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"400");
							}
						}
					
					} else if (subtest02.equals("admin.html"))
					{
						// Retornar dashboard
						String content = "text/html";
						byte[] fileData = dashboard().getBytes();
						int fileLength = (int) fileData.length;
						
						// metodo de conexao GET no protocolo HTTP 
						if (method.equals("GET"))
						{
							// send HTTP Headers
							out.println("HTTP/1.1 200 OK");
							out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
							out.println("Date: " + new Date());
							out.println("Content-type: " + content);
							out.println("Content-length: " + fileLength);
							out.println();
							out.flush();
							
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"200");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"200");
							}
						} else {
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"400");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"400");
							}
						}
					} else
					{
						File file = new File(WEB_ROOT, fileRequested);
						int fileLength = (int) file.length();
						String content = getContentType(fileRequested);
						
						// metodo de conexao GET no protocolo HTTP 
						if (method.equals("GET"))
						{
							byte[] fileData = lerArquivoDados(file, fileLength);
							
							// send HTTP Headers
							out.println("HTTP/1.1 200 OK");
							out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
							out.println("Date: " + new Date());
							out.println("Content-type: " + content);
							out.println("Content-length: " + fileLength);
							out.println();
							out.flush();
							
							dataOut.write(fileData, 0, fileLength);
							dataOut.flush();
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"200");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"200");
							}
						} else {
							ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"400");
							if (ControllerMain.DEBUG)
							{
								System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"400");
							}
						}
					}
				}
			}
			
		} catch (FileNotFoundException fnfe)
		{
			try
			{
				arquivoNaoEncontrado(out, dataOut, fileRequested, method);
			} catch (IOException ioe)
			{
				if (ControllerMain.DEBUG)
				{
					System.out.println("SYSError - FileNotFoundException: " + ioe.getMessage());
				}
			}
			
		} catch (IOException ioe)
		{
			if (ControllerMain.DEBUG)
			{
				System.out.println("SYSError - Erro de servidor: " + ioe.getMessage());
			}
		}  catch (Exception une)
		{
			try
			{
				requisicaoNaoCompreendida(out, dataOut, fileRequested, method);
			} catch (IOException ioe)
			{
				if (ControllerMain.DEBUG)
				{
					System.out.println("SYSError - Unknown - Erro de servidor: " + une.getMessage());
				}
			}
		} finally
		{
			try
			{
				in.close();
				out.close();
				dataOut.close();
				cliente.close(); // fecha a conexao do socket
				if (ControllerMain.DEBUG)
				{
					System.out.println("Conexao fechada com sucesso.");
				}
				ControllerMain.getInstance().generateLog(ControllerMain.SRV, "CloseOK");
			} catch (Exception e)
			{
				if (ControllerMain.DEBUG)
				{
					System.out.println("SYSError - Erro ao fechar conexao: " + e.getMessage());
				}
				//ControllerMain.getInstance().generateLog(ControllerMain.SRV, "CloseErr");
			}
		}
	}
	
	/**
	 * Monta o dashboard dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return dashboard montado com os dados do DB
	 */
	private String dashboard ()
	{
		// Recupera do DB os 10 mais acessados
		String dashboard;
		ArrayList<ResultTable> dados01 = ControllerMain.getInstance().getServiceAcc().lista403(true);
		ArrayList<ResultTable> dados02 = ControllerMain.getInstance().getServiceAcc().lista404(true);
		ArrayList<ResultTable> dados03 = ControllerMain.getInstance().getServiceAcc().listaDistintos();
		ArrayList<ResultTable> dados04 = ControllerMain.getInstance().getServiceAcc().reqsPorHora();
		ArrayList<ResultTable> dados05 = ControllerMain.getInstance().getServiceAcc().reqsPorMes();
		ArrayList<ResultTable> dados06 = ControllerMain.getInstance().getServiceAcc().listaMaisAcessados(10);
		ArrayList<ResultTable> dados07 = ControllerMain.getInstance().getServiceAcc().listaTopErros();
		ArrayList<ResultTable> dados08 = ControllerMain.getInstance().getServiceAcc().listaIPsFrequentes(10);
		ArrayList<ResultTable> dados09 = ControllerMain.getInstance().getServiceSrv().contaUltimos(5);
		ArrayList<ResultTable> dados10 = ControllerMain.getInstance().getServiceAcc().reqsPorDia();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS01: "+dados01.toString());
			System.out.println("DADOS01 - Size: "+dados01.size());
			System.out.println("DADOS02: "+dados02.toString());
			System.out.println("DADOS02 - Size: "+dados02.size());
			System.out.println("DADOS03: "+dados03.toString());
			System.out.println("DADOS03 - Size: "+dados03.size());
			System.out.println("DADOS04: "+dados04.toString());
			System.out.println("DADOS04 - Size: "+dados04.size());
			System.out.println("DADOS05: "+dados05.toString());
			System.out.println("DADOS05 - Size: "+dados05.size());
			System.out.println("DADOS06: "+dados06.toString());
			System.out.println("DADOS06 - Size: "+dados06.size());
			System.out.println("DADOS07: "+dados07.toString());
			System.out.println("DADOS07 - Size: "+dados07.size());
			System.out.println("DADOS08: "+dados08.toString());
			System.out.println("DADOS08 - Size: "+dados08.size());
			System.out.println("DADOS09: "+dados09.toString());
			System.out.println("DADOS09 - Size: "+dados09.size());
			System.out.println("DADOS10: "+dados10.toString());
			System.out.println("DADOS10 - Size: "+dados10.size());
		}
		
		// Preenche os dados no relatorio
		dashboard = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Dashboard</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		\r\n" + 
				"		var chart01 = new CanvasJS.Chart(\"403-doughnut-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"doughnut\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados01)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart02 = new CanvasJS.Chart(\"404-doughnut-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"doughnut\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados02)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart03 = new CanvasJS.Chart(\"ips-pie-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"pie\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados03)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart04 = new CanvasJS.Chart(\"acessoshorario-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados04)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart05 = new CanvasJS.Chart(\"acessosmes-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados05)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart06 = new CanvasJS.Chart(\"toparquivos-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados06)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart07 = new CanvasJS.Chart(\"toperros-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados07)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart08 = new CanvasJS.Chart(\"topips-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados08)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart09 = new CanvasJS.Chart(\"servidor-line-table\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados09)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		\r\n" + 
				"		var chart10 = new CanvasJS.Chart(\"acessosdia-column-chart\", {\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
				
				for (ResultTable dado : dados10)
				{
					dashboard = dashboard + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
				}
				dashboard = dashboard +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"\r\n" + 
				"		chart01.render();\r\n" + 
				"		chart02.render();\r\n" + 
				"		chart03.render();\r\n" + 
				"		chart04.render();\r\n" + 
				"		chart05.render();\r\n" + 
				"		chart06.render();\r\n" + 
				"		chart07.render();\r\n" + 
				"		chart08.render();\r\n" + 
				"		chart09.render();\r\n" + 
				"		chart10.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"	<!-- Barra de Navegacao -->\r\n" + 
				"	<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/Mes</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"    <!-- /Barra de Navegacao -->\r\n" + 
				"\r\n" + 
				"	<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Dashboard</h2>\r\n" + 
				"            <!-- <p class=\"lead\" align=center>P�gina usada para testes de gr�fico do CanvasJS.</p> -->\r\n" + 
				"            <br>\r\n" + 
				"        </div>\r\n" + 
				"        \r\n" + 
				"        <!-- 1st row -->\r\n" + 
				"		<div class=\"row m-b-2\" align=center>\r\n" + 
				"			<div class=\"col-lg-4\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title\" align=center>Erros 403</h4>\r\n" + 
				"					<div id=\"403-doughnut-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"			<div class=\"col-lg-4\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title\" align=center>Erros 404</h4>\r\n" + 
				"					<div id=\"404-doughnut-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"			<div class=\"col-lg-4\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title\" align=center>IPs Distintos</h4>\r\n" + 
				"					<div id=\"ips-pie-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"		</div>\r\n" + 
				"		<!-- /1st row -->\r\n" + 
				"	<hr align=center>\r\n" + 
				"		<!-- 2nd row -->\r\n" + 
				"		<div class=\"row\" align=center>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Acessos/Hora</h4>\r\n" + 
				"					<div id=\"acessoshorario-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Acessos/Mes</h4>\r\n" + 
				"					<div id=\"acessosmes-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"		</div>\r\n" + 
				"		<!-- /2nd row -->\r\n" + 
				"	<hr align=center>\r\n" + 
				"		<!-- 3rd row -->\r\n" + 
				"		<div class=\"row\" align=center>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Top Arquivos</h4>\r\n" + 
				"					<div id=\"toparquivos-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Top Erros</h4>\r\n" + 
				"					<div id=\"toperros-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"		</div>\r\n" + 
				"		<!-- /3rd row -->\r\n" + 
				"	<hr align=center>	\r\n" + 
				"		<!-- 4th row -->\r\n" + 
				"		<div class=\"row\" align=center>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Top IPs</h4>\r\n" + 
				"					<div id=\"topips-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"			<div class=\"col-lg-6\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title m-b-2\" align=center>Servidor</h4>\r\n" + 
				"					<div id=\"servidor-line-table\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"		</div>\r\n" + 
				"		<!-- /4th row -->\r\n" + 
				"	<hr align=center>\r\n" + 
				"		<!-- 5th row -->\r\n" + 
				"		<div class=\"row\">\r\n" + 
				"			<div class=\"col-md-12\">\r\n" + 
				"				<div class=\"card card-block\">\r\n" + 
				"					<h4 class=\"card-title\" align=center>Acessos/Dia</h4>\r\n" + 
				"					<div id=\"acessosdia-column-chart\" style=\"height: 200px; width: 100%;\"></div>\r\n" + 
				"				</div>\r\n" + 
				"			</div>\r\n" + 
				"		</div>\r\n" + 
				"		<!-- /5th row -->\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		if (ControllerMain.DEBUG)
		{
			//System.out.println("------------------------\n"+dashboard);
		}
		return dashboard;
	}
	
	/**
	 * Monta o relatorio01 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio01 montado com os dados do DB
	 */
	private String relatorio01 ()
	{
		// Recupera do DB os 10 mais acessados
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().listaMaisAcessados(10);
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 01</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Top 10 Arquivos\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n" + 
				"					{ label: \""+dados.get(0).getTipo()+"\", y: "+dados.get(0).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(1).getTipo()+"\", y: "+dados.get(1).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(2).getTipo()+"\", y: "+dados.get(2).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(3).getTipo()+"\", y: "+dados.get(3).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(4).getTipo()+"\", y: "+dados.get(4).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(5).getTipo()+"\", y: "+dados.get(5).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(6).getTipo()+"\", y: "+dados.get(6).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(7).getTipo()+"\", y: "+dados.get(7).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(8).getTipo()+"\", y: "+dados.get(8).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(9).getTipo()+"\", y: "+dados.get(9).getValor()+"  },\r\n" +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 01</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Arquivos mais acessados do servidor.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio02 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio02 montado com os dados do DB
	 */
	private String relatorio02 ()
	{
		// Recupera do DB o volume de requisicoes por hora
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().reqsPorHora();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 02</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Acessos/Horario\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n" + 
				"					{ label: \""+dados.get(0).getTipo()+"\", y: "+dados.get(0).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(1).getTipo()+"\", y: "+dados.get(1).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(2).getTipo()+"\", y: "+dados.get(2).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(3).getTipo()+"\", y: "+dados.get(3).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(4).getTipo()+"\", y: "+dados.get(4).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(5).getTipo()+"\", y: "+dados.get(5).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(6).getTipo()+"\", y: "+dados.get(6).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(7).getTipo()+"\", y: "+dados.get(7).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(8).getTipo()+"\", y: "+dados.get(8).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(9).getTipo()+"\", y: "+dados.get(9).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(10).getTipo()+"\", y: "+dados.get(10).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(11).getTipo()+"\", y: "+dados.get(11).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(12).getTipo()+"\", y: "+dados.get(12).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(13).getTipo()+"\", y: "+dados.get(13).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(14).getTipo()+"\", y: "+dados.get(14).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(15).getTipo()+"\", y: "+dados.get(15).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(16).getTipo()+"\", y: "+dados.get(16).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(17).getTipo()+"\", y: "+dados.get(17).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(18).getTipo()+"\", y: "+dados.get(18).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(19).getTipo()+"\", y: "+dados.get(19).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(20).getTipo()+"\", y: "+dados.get(20).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(21).getTipo()+"\", y: "+dados.get(21).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(22).getTipo()+"\", y: "+dados.get(22).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(23).getTipo()+"\", y: "+dados.get(23).getValor()+"  },\r\n" + 
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 02</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Hor&aacute;rios do dia com mais requisi&ccedil;&otilde;es.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio03 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio03 montado com os dados do DB
	 */
	private String relatorio03 ()
	{
		// Recupera do DB os IPs mais frequentes
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().listaIPsFrequentes(10);
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 03</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Top IPs\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n";
		
		for (ResultTable dado : dados)
		{
			relatorio = relatorio + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
		}
		relatorio = relatorio + "				]\r\n" +
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 03</h2>\r\n" + 
				"            <p class=\"lead\" align=center>IPs que mais enviaram requisi&ccedil;&otilde;es.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio04 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio04 montado com os dados do DB
	 */
	private String relatorio04 ()
	{
		// Recupera do DB os que causaram erro 404
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().lista404(true);
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 04</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Erros 404\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"doughnut\",\r\n" + 
				"				dataPoints: [\r\n";
		for (ResultTable dado : dados)
		{
			relatorio = relatorio + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
		}
		relatorio = relatorio + "				]\r\n" +
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 04</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Arquivos distintos que causaram erro HTTP 404.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio05 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio05 montado com os dados do DB
	 */
	private String relatorio05 ()
	{
		// Recupera do DB as requisicoes por dia
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().reqsPorDia();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 05</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Acessos/Dia\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n" + 
				"					{ label: \""+dados.get(0).getTipo()+"\", y: "+dados.get(0).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(1).getTipo()+"\", y: "+dados.get(1).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(2).getTipo()+"\", y: "+dados.get(2).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(3).getTipo()+"\", y: "+dados.get(3).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(4).getTipo()+"\", y: "+dados.get(4).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(5).getTipo()+"\", y: "+dados.get(5).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(6).getTipo()+"\", y: "+dados.get(6).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(7).getTipo()+"\", y: "+dados.get(7).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(8).getTipo()+"\", y: "+dados.get(8).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(9).getTipo()+"\", y: "+dados.get(9).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(10).getTipo()+"\", y: "+dados.get(10).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(11).getTipo()+"\", y: "+dados.get(11).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(12).getTipo()+"\", y: "+dados.get(12).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(13).getTipo()+"\", y: "+dados.get(13).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(14).getTipo()+"\", y: "+dados.get(14).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(15).getTipo()+"\", y: "+dados.get(15).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(16).getTipo()+"\", y: "+dados.get(16).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(17).getTipo()+"\", y: "+dados.get(17).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(18).getTipo()+"\", y: "+dados.get(18).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(19).getTipo()+"\", y: "+dados.get(19).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(20).getTipo()+"\", y: "+dados.get(20).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(21).getTipo()+"\", y: "+dados.get(21).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(22).getTipo()+"\", y: "+dados.get(22).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(23).getTipo()+"\", y: "+dados.get(23).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(24).getTipo()+"\", y: "+dados.get(24).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(25).getTipo()+"\", y: "+dados.get(25).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(26).getTipo()+"\", y: "+dados.get(26).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(27).getTipo()+"\", y: "+dados.get(27).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(28).getTipo()+"\", y: "+dados.get(28).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(29).getTipo()+"\", y: "+dados.get(29).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(30).getTipo()+"\", y: "+dados.get(30).getValor()+"  },\r\n" + 
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 05</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Acessos por dia.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio06 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio06 montado com os dados do DB
	 */
	private String relatorio06 ()
	{
		// Recupera do DB as requisicoes por mes
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().reqsPorMes();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 06</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Acessos/Mes\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n" + 
				"					{ label: \""+dados.get(0).getTipo()+"\", y: "+dados.get(0).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(1).getTipo()+"\", y: "+dados.get(1).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(2).getTipo()+"\", y: "+dados.get(2).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(3).getTipo()+"\", y: "+dados.get(3).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(4).getTipo()+"\", y: "+dados.get(4).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(5).getTipo()+"\", y: "+dados.get(5).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(6).getTipo()+"\", y: "+dados.get(6).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(7).getTipo()+"\", y: "+dados.get(7).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(8).getTipo()+"\", y: "+dados.get(8).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(9).getTipo()+"\", y: "+dados.get(9).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(10).getTipo()+"\", y: "+dados.get(10).getValor()+"  },\r\n" +
				"					{ label: \""+dados.get(11).getTipo()+"\", y: "+dados.get(11).getValor()+"  },\r\n" +
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 06</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Acessos por m&ecirc;s.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio07 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio07 montado com os dados do DB
	 */
	private String relatorio07 ()
	{
		// Recupera do DB os IPs distintos
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().listaDistintos();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 07</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - IPs Distintos\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"doughnut\",\r\n" + 
				"				dataPoints: [\r\n";
		for (ResultTable dado : dados)
        {
			relatorio = relatorio + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
        }
		relatorio = relatorio + "				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 07</h2>\r\n" + 
				"            <p class=\"lead\" align=center>IPs distintos que acessaram o servidor web.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio08 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio08 montado com os dados do DB
	 */
	private String relatorio08 ()
	{
		// Recupera do DB os logs de servidor
		String relatorio;
		ArrayList<AbstractLog> dados = ControllerMain.getInstance().getServiceSrv().listaUltimos(20);
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 08</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../css/cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 08</h2>\r\n" + 
				"           <p class=\"lead\" align=center>Logs de servi&ccedil;o do ServerWeb.</p>\r\n" + 
				"       </div>\r\n" + 
				"	    <div class=\"table-responsive\">\r\n" + 
				"			<table class=\"table table-hover\">\r\n" + 
				"	    	<thead>\r\n" + 
				"	      	<tr>\r\n" + 
				"	        	<th>Data</th>\r\n" + 
				"	        	<th>Log</th>\r\n" + 
				"		    </tr>\r\n" + 
				"		    </thead>\r\n" + 
				"		    <tbody>\r\n";
		for (AbstractLog log : dados)
		{
			relatorio = relatorio + 
					"		    <tr>\r\n" + 
					"		        <td>"+log.getData()+"</td>\r\n" + 
					"		        <td>"+log.getText()+"</td>\r\n" + 
					"		    </tr>\r\n";
		}
		relatorio = relatorio +
				"		    </tbody>\r\n" + 
				"		  </table>\r\n" + 
				"		</div>\r\n" + 
				"	</div>\r\n" +
				"\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio09 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio09 montado com os dados do DB
	 */
	private String relatorio09 ()
	{
		// Recupera do DB os que causaram erro 403
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().lista403(true);		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 09</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Erros 403\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"doughnut\",\r\n" + 
				"				dataPoints: [\r\n";
		for (ResultTable dado : dados)
		{
			relatorio = relatorio + "					{ label: \""+dado.getTipo()+"\", y: "+dado.getValor()+"  },\r\n"; 
		}
		relatorio = relatorio + "				]\r\n" +
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 09</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Arquivos distintos que causaram erro HTTP 403.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Monta o relatorio10 dinamicamente conforme dados recuperados no DB do webserver.
	 * 
	 * @return relatorio10 montado com os dados do DB
	 */
	private String relatorio10 ()
	{
		// Recupera do DB os 10 mais acessados
		String relatorio;
		ArrayList<ResultTable> dados = ControllerMain.getInstance().getServiceAcc().listaTopErros();
		
		if (ControllerMain.DEBUG)
		{
			System.out.println("DADOS: "+dados.toString());
			System.out.println("DADOS - Size: "+dados.size());
		}
		
		// Preenche os dados no relatorio
		relatorio = "<html lang=\"pt-br\">\r\n" + 
				"<head>\r\n" + 
				"    <meta charset=\"utf-8\">\r\n" + 
				"    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n" + 
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" + 
				"	<title>ECP7AN-MCA1-09 ..:::.. Relat&oacute;rio 10</title>\r\n" + 
				"	\r\n" + 
				"    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\r\n" + 
				"    <link href=\"../css/style.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- Custom styles for this template -->\r\n" + 
				"    <link href=\"../cover.css\" rel=\"stylesheet\">\r\n" + 
				"    \r\n" + 
				"    <!-- CanvasJS Data -->\r\n" + 
				"    <script src=\"https://canvasjs.com/assets/script/canvasjs.min.js\"></script>\r\n" + 
				"	<script type=\"text/javascript\">\r\n" + 
				"	window.onload = function () {\r\n" + 
				"		var chart = new CanvasJS.Chart(\"chartContainer\", {\r\n" + 
				"			title:{\r\n" + 
				"				text: \"Grafico - Top Erros\"              \r\n" + 
				"			},\r\n" + 
				"			data: [              \r\n" + 
				"			{\r\n" + 
				"				// Change type to \"doughnut\", \"line\", \"splineArea\", etc.\r\n" + 
				"				type: \"column\",\r\n" + 
				"				dataPoints: [\r\n" + 
				"					{ label: \""+dados.get(0).getTipo()+"\", y: "+dados.get(0).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(1).getTipo()+"\", y: "+dados.get(1).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(2).getTipo()+"\", y: "+dados.get(2).getValor()+"  },\r\n" + 
				"					{ label: \""+dados.get(3).getTipo()+"\", y: "+dados.get(3).getValor()+"  },\r\n" + 
				"				]\r\n" + 
				"			}\r\n" + 
				"			]\r\n" + 
				"		});\r\n" + 
				"		chart.render();\r\n" + 
				"	}\r\n" + 
				"	</script>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"<!-- Barra de Navegacao -->\r\n" + 
				"<nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n" + 
				"        <div class=\"container-fluid\">\r\n" + 
				"            <div class=\"navbar-header\">\r\n" + 
				"                <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n" + 
				"                    <span class=\"sr-only\">Toggle navigation</span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                    <span class=\"icon-bar\"></span>\r\n" + 
				"                </button>\r\n" + 
				"                <a class=\"navbar-brand\" href=\"../index.html\">ECP7AN-MCA1-09</a>\r\n" + 
				"            </div>\r\n" + 
				"            <div id=\"navbar\" class=\"navbar-collapse collapse\">\r\n" + 
				"                <ul class=\"nav navbar-nav navbar-right\">\r\n" + 
				"                    <li><a href=\"admin.html\">Dashboard</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio01.html\">Top 10 Arquivos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio02.html\">Acessos/Hora</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio03.html\">Top 10 IPs</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio09.html\">Erros 403</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio04.html\">Erros 404</a>\r\n" +  
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio10.html\">Top Erros</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio05.html\">Acessos/Dia</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio06.html\">Acessos/M&ecirc;s</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio07.html\">IPs Distintos</a>\r\n" + 
				"                    </li>\r\n" + 
				"                    <li><a href=\"relatorio08.html\">Servidor</a>\r\n" + 
				"                    </li>\r\n" + 
				"                </ul>\r\n" + 
				"            </div>\r\n" + 
				"        </div>\r\n" + 
				"    </nav>\r\n" + 
				"\r\n" + 
				"<!-- Corpo da Pagina -->\r\n" + 
				"    <div class=\"site-wrapper\">\r\n" + 
				"	    <div class=\"inner cover\">\r\n" + 
				"    	    <h2 class=\"cover-heading\" align=center>Administrativo - Relat&oacute;rio 10</h2>\r\n" + 
				"            <p class=\"lead\" align=center>Listagem de erros de acesso no servidor.</p>\r\n" + 
				"            <br>\r\n" + 
				"            <p class=\"lead\" align=center>\r\n" + 
				"            	<div id=\"chartContainer\" style=\"height: 300px; width: 100%;\"></div>\r\n" + 
				"            </p>\r\n" + 
				"        </div>\r\n" + 
				"\r\n" + 
				"        <div class=\"mastfoot\">\r\n" + 
				"            <div class=\"inner\" align=center>\r\n" + 
				"            	<br>\r\n" + 
				"        		<p>ECP7AN-MCA1-09</p>\r\n" + 
				"        	</div>\r\n" + 
				"        </div>\r\n" + 
				"    </div>\r\n" + 
				"    <script src=\"../js/jquery.min.js\"></script>\r\n" + 
				"    <script src=\"../js/bootstrap.min.js\"></script>\r\n" + 
				"</body>\r\n" + 
				"</html>";
		
		return relatorio;
	}
	
	/**
	 * Le o arquivo apontado pela solicitacao nos recursos web do servidor, e os retorna para o service web.
	 * Os arquivos de recursos web estao em SocketAdmin.WEB_ROOT.
	 * 
	 * @param file : arquivo solicitado
	 * @param fileLength : tamanho do arquivo
	 * @return Arquivo solicitado
	 * @throws IOException
	 */
	private byte[] lerArquivoDados(File file, int fileLength) throws IOException
	{
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		try
		{
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally
		{
			if (fileIn != null) 
				fileIn.close();
		}
		return fileData;
	}
	
	/**
	 * Retorna os ContentTypes MIME Types suportados pelo service web.
	 * 
	 * @param fileRequested : arquivo solicitado
	 * @return ContentType do arquivo
	 */
	private String getContentType(String fileRequested)
	{
		if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html"))
			return "text/html";
		else
			return "text/plain";
	}
	
	/**
	 * Processa uma requisicao de arquivo nao encontrado com erro HTML 404. 
	 * 
	 * @param out : writer de texto
	 * @param dataOut : writer de dados
	 * @param fileRequested : arquivo solicitado
	 * @param method 
	 * @throws IOException
	 */
	private void arquivoNaoEncontrado(PrintWriter out, OutputStream dataOut, String fileRequested, String method) throws IOException
	{
		File file = new File(WEB_ROOT, NOT_FOUND);
		int fileLength = (int) file.length();
		String content = "text/html";
		byte[] fileData = lerArquivoDados(file, fileLength);
		
		out.println("HTTP/1.1 404 Arquivo Nao Encontrado");
		out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println();
		out.flush();
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
		
		String ip = cliente.getInetAddress().getHostAddress();
		if (ControllerMain.DEBUG)
		{
			System.out.println("ACC: " + fileRequested+"#"+method+"#"+ip+"#"+"404");
		}
		ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"404");
	}
	
	/**
	 * Processa uma requisicao nao compreendida com erro HTML 400. 
	 * 
	 * @param out : writer de texto
	 * @param dataOut : writer de dados
	 * @param fileRequested : arquivo solicitado
	 * @param method 
	 * @throws IOException
	 */
	private void requisicaoNaoCompreendida(PrintWriter out, OutputStream dataOut, String fileRequested, String method) throws IOException
	{
		File file = new File(WEB_ROOT, UNKNOWN);
		int fileLength = (int) file.length();
		String content = "text/html";
		byte[] fileData = lerArquivoDados(file, fileLength);
		
		out.println("HTTP/1.1 400 Requisicao Nao Compreendida");
		out.println("Server: Servidor Java HTTP - ECP7AN-MCA1-09");
		out.println("Date: " + new Date());
		out.println("Content-type: " + content);
		out.println("Content-length: " + fileLength);
		out.println();
		out.flush();
		
		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
		
		String ip = cliente.getInetAddress().getHostAddress();
		if (ControllerMain.DEBUG)
		{
			System.out.println("ACC: "+fileRequested+"#"+method+"#"+ip+"#"+"400");
		}
		ControllerMain.getInstance().generateLog(ControllerMain.ACC, fileRequested+"#"+method+"#"+ip+"#"+"400");
	}
}