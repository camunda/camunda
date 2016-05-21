package org.camunda.tngp.broker.servicecontainer;

public interface ServiceName<S>
{
    String getName();

    static <S> ServiceName<S> newServiceName(final String name, Class<S> type)
    {
        return new ServiceNameImpl<>(name);
    }

    static class ServiceNameImpl<S> implements ServiceName<S>
    {
        protected final String name;

        public ServiceNameImpl(String name)
        {
            this.name = name;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return getName();
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ServiceNameImpl other = (ServiceNameImpl) obj;
            if (name == null)
            {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

    }
}
