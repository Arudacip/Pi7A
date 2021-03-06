package models.services;

import java.sql.Connection;
import java.util.ArrayList;

import models.AbstractLog;
import models.ResultTable;
import models.dao.DAOLogAcc;

/**
 * Classe do Controller Service a tratar do LogAcc.
 * @author Grupo ECP7AN-MCA1-09 - Bruno Gama, Guilherme Sant'Clair, Luis Felipe, Rafael Cassiolato, Raiza Morata.
 *
 * @param dao : DAO do log
 * @param conn : conexao com o banco de dados
 */

public class ServiceLogAcc
{
	
	private DAOLogAcc dao = new DAOLogAcc();
	private Connection conn = null;
	
	/**
	 * Construtor exclusivo para referencia da conexao com o banco de dados e recuperar logs.
	 * @param conn : conexao com o banco de dados
	 */
	public ServiceLogAcc(Connection conn)
	{
		this.conn = conn;
	}
	
	/**
	 * Inclui um LogAcc no banco de dados.
	 */
	public void incluir(AbstractLog log)
	{
		dao.setLog(log);
		dao.incluir(conn);
	}
	
	/**
	 * Recupera um LogAcc do banco de dados por ID.
	 * @return lista de LogAcc
	 */
	public AbstractLog carregaID()
	{
		return dao.carregaID(conn);
	}
	
	/**
	 * Recupera a lista dos ultimos X LogAcc do banco de dados.
	 * @return lista de LogAcc
	 */
	public ArrayList<AbstractLog> listaUltimos(int num)
	{
		return dao.listaUltimos(conn, num);
	}
	
	/**
	 * Recupera a lista dos X LogAcc mais acessados do banco de dados.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> listaMaisAcessados(int num)
	{
		return dao.listaMaisAcessados(conn, num);
	}

	/**
	 * Recupera a lista dos X IPs mais frequentes no banco de dados.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> listaIPsFrequentes(int num)
	{
		return dao.listaIPsFrequentes(conn, num);
	}

	/**
	 * Recupera a tabela de numero de acessos por horario no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> reqsPorHora()
	{
		ArrayList<ResultTable> resultados = new ArrayList<ResultTable>();
		ResultTable currentresult;
		
		// Loop que verifica cada hora
		for (int i=0; i <= 23; i++) {
			ArrayList<AbstractLog> logs = dao.listaHora(conn, ""+i+"");
			currentresult = new ResultTable(""+i+"h", +logs.size());
			resultados.add(currentresult);
		}
		return resultados;
	}

	/**
	 * Recupera a tabela de numero de acessos por dia no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> reqsPorDia()
	{
		ArrayList<ResultTable> resultados = new ArrayList<ResultTable>();
		ResultTable currentresult;
		
		// Loop que verifica cada hora
		for (int i=1; i <= 31; i++) {
			ArrayList<AbstractLog> logs = dao.listaDia(conn, ""+i+"");
			currentresult = new ResultTable(""+i+"", logs.size());
			resultados.add(currentresult);
		}
		return resultados;
	}

	/**
	 * Recupera a tabela de numero de acessos por mes no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> reqsPorMes()
	{
		ArrayList<ResultTable> resultados = new ArrayList<ResultTable>();
		ResultTable currentresult;
		
		// Loop que verifica cada hora
		for (int i=1; i <= 12; i++) {
			ArrayList<AbstractLog> logs = dao.listaMes(conn, ""+i+"");
			currentresult = new ResultTable(""+i+"", logs.size());
			resultados.add(currentresult);
		}
		return resultados;
	}

	/**
	 * Recupera a tabela de IPs distintos que acessaram o servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> listaDistintos()
	{
		return dao.listaDistintos(conn);
	}

	/**
	 * Recupera a tabela de arquivos que causaram erro HTTP 400 no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> lista400(boolean limit)
	{ 
		return dao.lista400(conn, limit);
	}

	/**
	 * Recupera a tabela de arquivos que causaram erro HTTP 403 no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> lista403(boolean limit)
	{
		return dao.lista403(conn, limit);
	}

	/**
	 * Recupera a tabela de arquivos que causaram erro HTTP 404 no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> lista404(boolean limit)
	{
		return dao.lista404(conn, limit);
	}

	/**
	 * Recupera a tabela de arquivos que causaram erro HTTP 405 no servidor.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> lista405(boolean limit)
	{
		return dao.lista405(conn, limit);
	}
	
	/**
	 * Recupera a lista dos X erros por tipo mais presentes no banco de dados.
	 * @return lista de LogAcc
	 */
	public ArrayList<ResultTable> listaTopErros()
	{
		ArrayList<ResultTable> dado01 = lista400(false);
		ArrayList<ResultTable> dado02 = lista403(false);
		ArrayList<ResultTable> dado03 = lista404(false);
		ArrayList<ResultTable> dado04 = lista405(false);
		
		ArrayList<ResultTable> dados = new ArrayList<ResultTable>();
		dados.add(new ResultTable("Erro 400", dado01.size()));
		dados.add(new ResultTable("Erro 403", dado02.size()));
		dados.add(new ResultTable("Erro 404", dado03.size()));
		dados.add(new ResultTable("Erro 405", dado04.size()));
		return dados;
	}
	
	/**
	 * Recupera a lista de todos os LogAcc do banco de dados.
	 * @return lista de LogAcc
	 */
	public ArrayList<AbstractLog> listaTodos()
	{
		return dao.listaTodos(conn);
	}
}